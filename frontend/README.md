# Frontend

Owners: Abhi (workspace and visualization), Rudra (dashboard)

## Pages
- `workspace.html`: question box, code editor, failing test box, Run button (Abhi)
- `result.html`: side-by-side replay, explanation, hints, score, complexity (Abhi)
- `dashboard.html`: past submissions, scores, repeated mistake types (Rudra)

## Visualization
- Two runs move together, with the first difference highlighted.
- Controls: back, forward and jump to the first difference.
- Draw arrays, strings and hash maps first. Add linked lists and trees later.
- Only draw what the trace contains. Never guess a step.

## Stack
Plain HTML, CSS and JavaScript, with Monaco Editor for code. No framework needed.

## Talking to the backend
Pages call `http://localhost:8080/api/submissions` with `fetch` and show the JSON reply. See `docs/api-contract.md` for the exact format.

Start with hardcoded sample data, then connect to the backend once the fake endpoint is ready.

## Rule of thumb
Abhi and Rudra work in separate files so their pull requests do not collide. Agree on colours and layout together so the pages look like one product.
