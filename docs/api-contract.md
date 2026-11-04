# API Contract

This is the agreed data format. Do not change it without telling the whole team.

## Frontend to Backend

### Submit code

`POST /api/submissions`

Request:
```json
{
  "language": "python",
  "question": "Given a sorted array, return the 1-based positions of the two numbers that add up to target.",
  "code": "class Solution: ...",
  "failingTest": {
    "method": "twoSum",
    "args": { "numbers": [2, 7, 11, 15], "target": 9 },
    "expected": [1, 2]
  }
}
```

Response:
```json
{
  "id": "abc123",
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
  "score": 0,
  "hint": { "level": 1, "text": "When the total is too big, which pointer makes it smaller?" }
}
```

Field rules:
- `language` is `python` for now.
- `outcome` is one of `NORMAL`, `EXCEPTION`, `TIMED_OUT`, `TRUNCATED`.
- `score` is a whole number from 0 to 100.
- `line` is the line number in the submitted code.
- `correctTrace` and `firstDifference` are `null` when no tested correct version is available or the two runs can't be matched.
- The correct version is never included in this response.

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

If only the AI fails, return the real test result and trace, with `explanation` set to `null`.

## Backend to Runner

`POST /run`

Request:
```json
{
  "language": "python",
  "code": "class Solution: ...",
  "method": "twoSum",
  "args": { "numbers": [2, 7, 11, 15], "target": 9 }
}
```

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

`event` is one of `call`, `line`, `return`.
