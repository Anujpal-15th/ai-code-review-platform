# API Contract

This is the agreed data format. Do not change it without telling the whole team.

## Dashboard to Backend

`POST /api/review`

Request:
```json
{
  "language": "python",
  "code": "def add(a, b): return a - b"
}
```

Response:
```json
{
  "score": 72,
  "issues": [
    {
      "line": 1,
      "severity": "high",
      "title": "Wrong operator",
      "explanation": "The function is named add but subtracts.",
      "fix": "return a + b"
    }
  ]
}
```

Field rules:
- `severity` is one of `high`, `medium`, `low`.
- `score` is a whole number from 0 to 100.
- `line` is the line number in the submitted code.

Errors: invalid or empty code or language returns HTTP 400 with `{ "error": "message" }`.

## Backend to Analysis Service (Version 2)

The backend sends the code file and receives findings:
```json
[
  { "line": 1, "severity": "high", "message": "Example finding" }
]
```
