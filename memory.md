# MentorLens Project Memory

Read this first to pick the project back up. Update it after every finished task.

Last updated: 4 Nov 2026

## What this is
MentorLens helps students whose LeetCode-style code fails. They paste the question, their `Solution` code and the failing test. It really runs the code, shows it step by step beside a verified correct version, stops at the first difference, and explains the mistake with hints before the answer.

The repo explainer, developer code review and users' own API keys are for later.

## Where to find things
| What | Where |
|---|---|
| Design (wins if documents disagree) | `docs/superpowers/specs/2026-11-04-mentorlens-design.md` |
| Task plan | `docs/superpowers/plans/2026-11-04-mentorlens-alpha.md` |
| JSON formats | `docs/api-contract.md` |
| Architecture | `docs/architecture.md` |
| Team rules | `docs/team-rules.md` |

## Team
| Person | Work | Tasks |
|---|---|---|
| Anuj (leader) | Backend, AI feedback, reviews and merges pull requests | 1, 5, 6, 7, 8, 9, 14 |
| Aryan | Python runner and sandbox | 2, 3, 4 |
| Abhi | Pages, visualization, consent checkbox | 10, 11, 12, frontend of 14 |
| Rudra | Samples, dashboard, docs | 13, 15 |

## Working rules
- All parts communicate through JSON over HTTP.
- Each task uses subagents to build and review, and goes in its own branch and pull request.
- After each task: update this file, explain the task to Anuj, and ask before starting the next.
- Only Anuj merges. Claude reviews teammates' pull requests when told they are open.
- Never add a `Co-Authored-By` line to commits. 15 older commits on `main` still have one, and removing them is on hold.
- Dates are not decided, so do not assume deadlines.
- Never put API keys or passwords in the repo.

## Tech
Frontend: HTML, CSS, JavaScript, Monaco. Backend: Java 17 target, Spring Boot 4, Maven Wrapper, PostgreSQL (H2 in tests). Runner: Python, FastAPI, `sys.settrace`, Docker. AI: Gemini first, key in `AI_API_KEY`.

## Work done
| Work | Pull request | Result |
|---|---|---|
| New README and docs | 1, 2 | Merged |
| Task 1: design, plan, docs synced | 3 | Merged |
| Task 5: login, submissions, queue with 4 workers | 4 | Merged |
| Task 6: first difference between two runs | 5 | Merged |
| Task 7: Gemini client with validated JSON | 6 | Merged |
| Task 8: submission pipeline and score | 7 | Merged |
| Automatic checks: tests, CodeQL, Dependabot | 8 | Merged |
| GitHub Actions version updates | 9, 10, 11, 12 | Merged |
| Task 9: hints by level | 13 | Merged |
| Task 14 backend: consent and pilot groups | 14 | Merged |

Also done: `main` has a `protect-main` rule requiring `backend-tests`, `runner-tests` and `frontend-tests`, with 0 approvals. Anuj created the rule on GitHub from settings Claude checked, and Claude has not confirmed it is active.

## In progress: final backend fixes
Branch `anuj-backend-final-fixes`, in a pull request waiting for Anuj's merge. 123 tests pass on H2.

A whole-backend review on real PostgreSQL found problems. Committed in `735a2d9` and `486f6a9`, 122 tests passing, then re-checked:
- Long expected answers no longer leave a submission stuck.
- CORS, consistent error replies, NUL rejection and a 256 KB body limit.
- Incomplete AI replies can't score 100.
- At most 3 active submissions per student, and optional `ALLOWED_EMAIL_DOMAIN`.

The re-check then found `/%61pi/auth/login` bypassed the login rate limit. Fixed in `9734ece`, with a real-server test seen failing without the fix. Claude read that diff, but no fresh subagent re-checked it.

## Not built
- Runner (Tasks 2-4): nothing on `main` except a README and `pytest.ini`.
- Frontend (Tasks 10-12) and consent checkbox: sign-up needs `"consent": true` from the frontend.
- Samples (Task 13): only `two_sum_sorted_wrong_pointer` exists.
- Dashboard (Task 15): `GET /api/students/me/progress` doesn't exist.

## Open decisions
1. **Team split, waiting for Anuj.** Proposal: Anuj keeps the backend, JSON passing and the AI call. Aryan's Python service takes the runner and analysis (`/analyze`: Pylint findings, measured complexity, first difference, score). This would move Java code to Python and change the design, plan and contract. Fallback: move only Pylint and measured complexity.
2. Whether to send Aryan his message, which waits on decision 1.
3. Who pays for AI and the server, and the budget.
4. Alpha and pilot dates.
5. Dr. Shukla's approval of the new scope. The synopsis still describes the old design, and its PDF has only the cover page.

## Known limits
- Never run end to end with a real runner or real Gemini.
- Failed AI calls count toward the daily limit of 20, which can overshoot by 3.
- `1.0` and `1` count as different. Questions with several valid answers can be wrongly marked as failing.
- Docker sandbox tests don't run in automatic checks.
- Assign pilot groups before students start, because unassigned students see the comparison.

## Check in Aryan's pull requests
- Student code can't fake the reply, including by printing JSON.
- `/run` isn't reachable from the internet.
- Variables exclude `self` and memory addresses, and every step's `line` is an integer.
- `output` holds the changed input for in-place questions.
- `runner/requirements.txt` is added with the first test.
