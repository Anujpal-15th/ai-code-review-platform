# Frontend (Dashboard)

Owners: Abhi (input page), Rudra (report page)

## Pages
- `input.html`: code box, language dropdown, Submit button (Abhi)
- `report.html`: score and list of issues with severity, explanation, fix (Rudra)

## Stack
Plain HTML, CSS and JavaScript for Month 1. No framework needed.

## Talking to the backend
Pages send the code with `fetch` to `http://localhost:8080/api/review` and show the JSON reply. See `docs/api-contract.md` for the exact format.

Start with hardcoded sample data, then connect to the backend once the fake endpoint is ready.

## Rule of thumb
Abhi and Rudra work in separate files so their pull requests do not collide. Agree on colours and layout together so the two pages look like one product.
