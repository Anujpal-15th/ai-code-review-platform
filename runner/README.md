# Runner Service (Python)

Owner: Aryan

## Responsibilities
- Take a bare `Solution` class and add the hidden parts: imports, driver, input conversion and helper types
- Run the code in a Docker sandbox with no network, plus time and memory limits
- Record each step with `sys.settrace`: line, variables and event
- Return the output and steps to the backend as JSON
- Queue runs and limit how many run at once

The request and response format is in `docs/api-contract.md`.

## Build first
A plain script, with no web service or Docker yet, that runs `tests/samples/two_sum_sorted_wrong_pointer.py` and prints its steps as JSON.

Then add these in order:
1. Limits on steps and time, so endless loops stop
2. The Docker sandbox
3. The web service
4. The queue

## Run locally
```
python -m venv .venv
source .venv/bin/activate
pip install fastapi uvicorn
```

Put your scripts in this folder. The service will run on port 8000 (backend uses 8080).

## Safety
Never run submitted code outside the sandbox on any shared or public server.
