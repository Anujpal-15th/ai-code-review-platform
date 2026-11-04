# API Contract

This is the agreed data format. Do not change it without telling the whole team.

## Frontend to Backend

### Sign up and log in

`POST /api/auth/register` and `POST /api/auth/login`

Request (login): `{ "email": "a@b.com", "password": "..." }`

Request (register): `{ "email": "a@b.com", "password": "...", "consent": true }`. `consent` must be `true` (the student agreed to the pilot terms). If it is missing or `false`, the response is HTTP 400 with `{ "error": "message" }` and no account is created.

When the server sets `ALLOWED_EMAIL_DOMAIN`, register also returns HTTP 400 with `{ "error": "message" }` for an email at any other domain.

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
  "error": null,
  "result": {
    "outcome": "NORMAL",
    "error": null,
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
- `error` is `null` unless `status` is `FAILED`, when it is a message string.
- `outcome` is one of `NORMAL`, `EXCEPTION`, `TIMED_OUT`, `TRUNCATED`, `UNSUPPORTED`. `UNSUPPORTED` means the question type is not supported yet.
- `score` is a code quality score, a whole number from 0 to 100. It is `null` when no checked AI reply is available, or when the AI reported issues and none could be used. It is a guide, not a grade.
- `line` is the line number in the submitted code.
- `correctTrace` is `null` when no tested correct version is available. `firstDifference` is `null` in that case, and also when the runs can't be aligned or no difference was found.
- Pilot: for students in the explanation-only group, `correctTrace` and `firstDifference` are always `null`, even when a tested correct version exists. Everything else (the student's own trace, `explanation`, `score`, hints, and the level 4 correct code) is unchanged. The page must treat these two as "not shown" and must not infer from them that no checked explanation is available. The group is set by admins and is not exposed by any API.
- `result.error` is the error message from the real run of the student's code, such as a crash message, or `null` if there was none. It is separate from the top-level `error`.
- `explanation` is `null` when the AI is unavailable or its reply is not usable.
- `noCheckedExplanation` is an extra boolean, present and `true` only when the AI replied but its correct version failed the test. All AI fields are then `null`, and the page should say no checked explanation is available. It is left out otherwise.
- The correct version is never included in this response.

### Get the next hint

`GET /api/submissions/{id}/hints/next`

```json
{ "level": 2, "text": "Look at line 9 with the test above.", "correctCode": null }
```

Levels: 1 nudge, 2 pointer, 3 guided fix, 4 correct version. `correctCode` is only filled in at level 4.

The result already shows level 1, so the first call returns level 2. Each call moves one level on. Once at level 4, further calls repeat level 4.

Returns HTTP 404 with `{ "error": "message" }` when the submission does not exist, belongs to another student, is not `DONE`, or has no checked AI reply.

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
| Invalid input, such as empty code, an unknown language, a NUL character in a text field, or a malformed body | HTTP 400 with `{ "error": "message" }` |
| No token, or an invalid one, or a token for a user that no longer exists | HTTP 401 with `{ "error": "message" }` |
| Submission id does not exist or belongs to another student | HTTP 404 with `{ "error": "message" }` |
| Wrong HTTP method | HTTP 405 with `{ "error": "message" }` |
| Request body over 256 KB | HTTP 413 with `{ "error": "message" }` |
| `Content-Type` is not JSON | HTTP 415 with `{ "error": "message" }` |
| A student already has 3 submissions `QUEUED` or `RUNNING`, or too many requests to `/api/auth/**` from one address | HTTP 429 with `{ "error": "message" }` |
| Unexpected server error | HTTP 500 with `{ "error": "message" }` |
| The runner failed while processing | The check call returns `status` `FAILED` with `error` set. Not an HTTP error. |
| Only the AI failed | The real result and trace, with `explanation` set to `null` |

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

Every step's `line` is always an integer. A reply that breaks this cannot be parsed, and the submission ends as `FAILED`.

`variables` must not include `self` or memory addresses (such as `<Foo object at 0x7f...>`). They differ between runs and would show up as false differences.
