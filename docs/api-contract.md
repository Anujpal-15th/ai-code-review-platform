# API Contract

This is the agreed data format. Do not change it without telling the whole team.

## Frontend to Backend

### Sign up and log in

`POST /api/auth/register` and `POST /api/auth/login`

Request (both): `{ "email": "a@b.com", "password": "..." }`

Response (both): `{ "token": "..." }`. Send it on later calls as `Authorization: Bearer <token>`.

### Submit code

Submitting and checking for the result are two calls, because submissions wait in a queue.

`POST /api/submissions`

Request:
```json
{
  "language": "python",
  "question": "Given a sorted array, return the 1-based positions of the two numbers that add up to target.",
  "code": "class Solution: ...",
  "failingTest": {
    "inputText": "numbers = [2,7,11,15], target = 9",
    "expectedText": "[1,2]"
  }
}
```

`inputText` and `expectedText` are pasted as the coding platform shows them. The backend parses them.

Response: HTTP 202
```json
{ "id": "abc123", "status": "QUEUED" }
```

### Check a submission

`GET /api/submissions/{id}`

```json
{
  "status": "DONE",
  "queuePosition": 0,
  "result": {
    "outcome": "NORMAL",
    "passed": false,
    "actualOutput": [],
    "expectedOutput": [1, 2],
    "studentTrace": [],
    "correctTrace": [],
    "firstDifference": { "step": 8, "line": 9, "variable": "left", "yours": 1, "correct": 0 },
    "explanation": {
      "what": "Your code returned [] and the expected answer was [1, 2].",
      "where": "On line 9, left moved when the total was too big.",
      "why": "In a sorted array, moving right is what makes the total smaller."
    },
    "mistakeType": "wrong-pointer-moved",
    "complexity": { "time": "O(n)", "space": "O(1)" },
    "score": 72,
    "hint": { "level": 1, "text": "When the total is too big, which pointer makes it smaller?" }
  }
}
```

Field rules:
- `language` is `python` for now.
- `status` is one of `QUEUED`, `RUNNING`, `DONE`, `FAILED`. `result` is `null` until `DONE`. `queuePosition` is `0` once it is no longer waiting.
- `outcome` is one of `NORMAL`, `EXCEPTION`, `TIMED_OUT`, `TRUNCATED`, `UNSUPPORTED`. `UNSUPPORTED` means the question type is not supported yet.
- `score` is a code quality score, a whole number from 0 to 100, or `null` when no checked AI reply is available. It is a guide, not a grade.
- `line` is the line number in the submitted code.
- `correctTrace` and `firstDifference` are `null` when no tested correct version is available or the two runs can't be matched.
- `explanation` is `null` when the AI is unavailable or its reply is not usable.
- The correct version is never included in this response.
- There are no dates in any request or response.

### Get the next hint

`GET /api/submissions/{id}/hints/next`

```json
{ "level": 2, "text": "Look at line 9 with the test above.", "correctCode": null }
```

Levels: 1 nudge, 2 pointer, 3 guided fix, 4 correct version. `correctCode` is only filled in at level 4.

### Get progress

`GET /api/students/me/progress`

```json
{
  "submissions": 12,
  "averageScore": 64,
  "hintsUsed": 20,
  "mistakeTypes": [{ "type": "off-by-one", "count": 5 }]
}
```

### Errors

| Case | Response |
| --- | --- |
| Invalid input, such as empty code or an unknown language | HTTP 400 with `{ "error": "message" }` |
| The AI or runner service failed | HTTP 502 with `{ "error": "message" }` |

The student's code crashing or timing out is **not** an API error. It returns normally with `outcome` set to `EXCEPTION` or `TIMED_OUT`.

A submission that fails inside the backend ends with `status` `FAILED` and a message.

If only the AI fails, return the real test result and trace, with `explanation` set to `null`.

## Backend to Runner

`POST /run`

Request:
```json
{
  "language": "python",
  "code": "class Solution: ...",
  "inputText": "numbers = [2,7,11,15], target = 9"
}
```

The runner reads the method name and parameter names from the code, and converts `inputText` into arguments.

Response:
```json
{
  "outcome": "NORMAL",
  "output": [],
  "stdout": "",
  "error": null,
  "steps": [
    {
      "step": 1,
      "line": 2,
      "event": "call",
      "function": "twoSum",
      "variables": { "numbers": [2, 7, 11, 15], "target": 9 }
    }
  ]
}
```

`outcome` is one of `NORMAL`, `EXCEPTION`, `TIMED_OUT`, `TRUNCATED`, `UNSUPPORTED`.

`event` is one of `call`, `line`, `return`.
