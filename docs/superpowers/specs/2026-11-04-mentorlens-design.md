# MentorLens Design

Status: awaiting approval. Items marked **(my call)** were proposed by Claude. Anuj accepted the recommendations for scoring, supported questions, alpha scope, server and budget, and the failing test, team roles, hints-first order and removing Pylint and Bandit. The remaining marks are for Anuj to check.

`docs/api-contract.md` and `docs/architecture.md` still show the older score and a single-request submission. Update them to match this document.

## Intent

A student's LeetCode or HackerRank-style solution fails and they don't understand why. They paste it into MentorLens, which adds the parts those platforms hide, really runs it, shows how it executed, and helps them see where their thinking went wrong and how to fix it.

The aim is understanding, not just a passing answer. The product question is "why did my code fail, and how do I stop making this mistake?"

## Users

College students practising DSA and coding questions. They are often stuck on one specific failing question.

## Goals

1. Run a bare `Solution` class, with no `main` function, the way a coding platform would.
2. Show a step-by-step visualization taken from real execution.
3. Show where the mistake is, using the first difference from a correct run on the same test.
4. Give a score, time and space complexity, tips, a mistake type and an explanation.
5. Show a correct version and how it differs from the student's code, after hints **(hints first is my call)**.

## Non-goals for this version

- Repo explainer (planned next product stage, needs its own design)
- Developer code review
- Users supplying their own AI API key
- Languages other than Python for submitted code
- Pylint and Bandit **(my call)**
- Plagiarism detection and formal grading
- Teacher accounts or classroom management

## Anti-goals

These would make it a failure even if it technically works:

- A visualization containing steps that did not really happen
- Telling a student correct code is wrong because of an unverified AI-written test
- Comparing against a "correct" version that was never run
- Becoming a chatbot that hands over the answer immediately
- Running submitted code outside the sandbox on a shared server

## Student flow

1. The student enters the question, their code, and the failing test with its expected output **(requiring the failing test is my call)**.
2. MentorLens runs the code on that test.
3. The AI receives the real result and writes a correct version, explanation, hints, complexity and mistake type.
4. The correct version runs on the same test. It is only used if it passes.
5. The student sees both runs side by side, stopped at the first difference, with the explanation layers and a level 1 hint.
6. They can request more hints: nudge, pointer, guided fix, then the correct version with changes marked.
7. The result is saved, and repeated mistake types appear on their dashboard.

## Test input format

The student pastes test input as shown by the coding platform and picks nothing else if it can be parsed. Exact parsing of platform formats is left to the builder. Parameter names come from the method signature.

## Comparing runs

- The AI is asked for a correct version that keeps the student's approach.
- A first difference is the first step where the executed line's result or shared variable values differ, after aligning runs by step.
- If runs cannot be meaningfully aligned, such as with a different approach, skip the marker and show each run plus where the output goes wrong.
- The exact alignment method is left to the builder, and must be checked on the sample dataset.

## Scoring

Pass or fail on the student's test is shown separately and comes only from the real run.

The score is a code quality score out of 100: `Q = max(0, 100 - sum(weight x count))`.

- It comes from AI-reported quality issues, with bugs weighted above style, and weights are tuned during testing.
- It is presented as a guide, because AI reviews vary a little between runs.
- If no checked AI reply is available, no score is shown, so `score` can be `null`.
- AI-written tests are not part of this version.

## Supported questions

- `Solution` class questions with one method call, including ones that change the input in place
- Data: arrays, strings, hash maps, linked lists and trees
- Not yet, with a clear "not supported yet" message: multi-call design questions such as LRU Cache, graph-specific drawings, and code that reads its own input

## Alpha scope

- Must have for the alpha: run and replay, comparison, explanation, hints, login and queue
- First to cut: the dashboard, then the score and complexity display
- Leave time for fixes between the alpha and the pilot

## Server and AI budget

- One cloud server with 4 vCPU and 8 GB RAM
- The cheapest suitable AI model, limited to about 20 submissions per student per day
- Submissions queue, and the page shows progress while the student waits, so the API submits first and then checks for the result
- Who pays and the exact budget are still unknown

## Failure behaviour

| Failure | What the student gets |
| --- | --- |
| Student code crashes or times out | A normal result showing the crash or timeout |
| AI reply invalid | One retry, then the real run with no AI explanation |
| AI service down | The real run and a clear message |
| Correct version fails the test | No comparison, and no correct version shown |
| Runner down | HTTP 502 with a clear message |

## How it is built (existing team decisions)

- Frontend: HTML, CSS, JavaScript and Monaco Editor
- Backend: Java 17+ and Spring Boot, the only component calling other services
- Runner: Python 3.10+, FastAPI and `sys.settrace`
- Sandbox: Docker with a fresh container per run, no network, time and memory limits, read-only file system except a temporary directory, non-root user
- Database: PostgreSQL
- AI: hosted model API with the key on the server (provider not chosen)

Formats live in `docs/api-contract.md` and flow in `docs/architecture.md`. This document wins if they disagree.

## Constraints

- Team of four: Anuj (backend, AI, scoring), Aryan (runner, sandbox), Abhi (workspace, visualization), Rudra (dashboard, samples, docs). **(Revised roles are my call.)**
- Dates are not decided yet.
- Must handle a class of 60 submitting together on a small server, using a queue.
- AI cost needs a per-student daily limit **(my call)**.
- Must tell users their code is sent to a third-party AI provider.

## Evaluation

The December pilot compares step-based feedback with AI explanation alone, using a short quiz **(my call)**. Also measure whether known mistakes are correctly located across at least 30 samples, ideally real student mistakes.

## Build order

1. One working path: paste, run, see steps
2. Side-by-side comparison and explanation
3. Hints, score and complexity
4. Dashboard and queue
5. If time allows: smallest failing test, predict the next step, mistake tracker

## Settled after review (my call)

- **Code passes the pasted test:** tell the student it passed, skip the AI, and save the run. Ask them to check they pasted the right test.
- **Judging output:** exact match after parsing to data. Questions accepting several valid answers are a known limitation, and the result page warns that the expected output may have more than one valid form.
- **Correct version fails the test:** hide all of that AI reply, because it may be wrong. Show the real run and say no checked explanation is available. Don't retry, and don't count it on the dashboard.
- **Pilot setup:** two separate student groups, set by a per-student switch that hides the comparison. The quiz is an outside form written by the team. Students log in so results can be matched, and they give consent first.
- **Daily AI limit reached:** the student gets the real run only, the same as when the AI is down.

## Left to the builder

Database schema, code structure, step limits, visualization library choices, how accounts or identity work beyond what the dashboard needs, and task breakdown.
