# AI Powered Developer Code Review and Analysis Platform

A web platform where a developer pastes code and an AI reviews it. The platform returns a quality score and a list of issues. Each issue has a severity, an explanation and a suggested fix.

## Team

| Member | Role | Folder |
| --- | --- | --- |
| Anuj | Team Leader, Backend, AI integration | `backend/` |
| Aryan | Analysis service (Python) | `analysis/` |
| Abhi | Input page (frontend) | `frontend/` |
| Rudra | Report page (frontend) | `frontend/` |

## How it works

1. The user pastes code and picks a language on the dashboard.
2. The dashboard sends it to the Spring Boot backend (`POST /api/review`).
3. The backend asks the AI to review the code (and, from Version 2, sends it to the Python analysis service first).
4. The backend calculates the quality score, saves the report and returns it.
5. The dashboard shows the score and the list of issues.

See `docs/architecture.md` for the diagram and `docs/api-contract.md` for the data format.

## Repository structure

```
ai-code-review-platform/
├── backend/        Spring Boot server (leader)
├── analysis/       Python analysis service (Aryan)
├── frontend/       Dashboard pages (Abhi, Rudra)
├── tests/          Sample code and expected results
├── docs/           Architecture, API contract, team rules
└── README.md
```

## Plan

- Version 1 (Month 1): paste code, get an AI review, see a report.
- Version 2 (Month 2): GitHub repo links, Python static analysis, saved history.
- Version 3 (Month 3): testing, polish, final report, demo.

## How to contribute

Read `docs/team-rules.md` before your first commit. In short: never push to `main`, work on your own branch, and open a small pull request for every task.
