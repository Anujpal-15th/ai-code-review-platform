# Analysis Service (Python)

Owner: Aryan

## Responsibilities
- Take a code file and run static analysis tools (Pylint, Bandit)
- Convert the results into one common JSON format
- Return the findings to the backend (Version 2: runs as a small web service)

## Output format (agreed with backend)
A list of findings, each with `line`, `severity` and `message`:

```json
[
  { "line": 1, "severity": "high", "message": "Example finding" }
]
```

## Run locally
```
python -m venv .venv
source .venv/bin/activate
pip install pylint bandit
```

Put your scripts in this folder. The service will run on port 8000 (backend uses 8080).
