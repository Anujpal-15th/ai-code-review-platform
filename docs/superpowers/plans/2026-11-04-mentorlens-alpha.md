# MentorLens Alpha Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pilot-ready alpha: a student submits a failing `Solution`, sees its real run beside a verified correct run, stopped at the first difference, with explanation and hints.

**Architecture:** A static frontend calls a Spring Boot backend, which queues each submission and is the only component calling other services. A Python runner executes code in a fresh Docker container and returns recorded steps. The AI supplies only teaching content and a correct version, which must pass the real test before use.

**Tech Stack:** HTML, CSS, JavaScript, Monaco Editor; Java 17+, Spring Boot, PostgreSQL; Python 3.10+, FastAPI, `sys.settrace`; Docker.

**Spec:** `docs/superpowers/specs/2026-11-04-mentorlens-design.md`

## Global Constraints

- Submitted language: `python` only.
- Supported: `Solution` class, one method call, in-place changes; arrays, strings, hash maps, linked lists, trees. Anything else returns `UNSUPPORTED`.
- Steps come only from real execution. An unverified correct version is never shown or compared.
- Output judged by exact match after parsing to data.
- Container: fresh per run, no network, time and memory limits, read-only except a temporary directory, non-root.
- Server target: 4 vCPU, 8 GB RAM, class of 60 submitting together.
- AI limit: 20 submissions per student per day. AI key only in a backend environment variable.
- Hint order: nudge, pointer, guided fix, correct version.
- Users are told their code goes to a third-party AI provider.
- If time runs short, cut the dashboard first, then score and complexity display.
- Out of scope: repo explainer, user API keys, AI-written tests, Pylint, Bandit, smallest failing test, predict-next-step, mistake tracker extras.

## Review Focus

- Endless loop in student code: stops, and the steps so far can still be replayed (Task 3).
- Syntax error or crash in student code: a normal result, not an API error (Task 3).
- Code trying to reach the network or write outside the temporary directory: blocked, and the server is unaffected (Task 4).
- Instructions inside student code comments asking the AI to reveal the answer: the correct version stays hidden until level 4 (Task 9).
- Page reload while queued: the student gets the same result without resubmitting (Task 12).

---

## Shared contracts

**Runner step:** `{step:int, line:int, event:"call"|"line"|"return", function:str, variables:object}`

**Runner result:** `{outcome:"NORMAL"|"EXCEPTION"|"TIMED_OUT"|"TRUNCATED"|"UNSUPPORTED", output:any, stdout:str, error:str|null, steps:[Step]}`

**API change from the older contract:** `POST /api/submissions` returns HTTP 202 `{id,status:"QUEUED"}`. `GET /api/submissions/{id}` returns `{status:"QUEUED"|"RUNNING"|"DONE"|"FAILED", queuePosition, result}`. `failingTest` is `{inputText, expectedText}`.

### Task 1: Save the design and sync the docs

**Owner:** Anuj

**Files:**
- Add: the spec and this plan
- Modify: `docs/api-contract.md`, `docs/architecture.md`, `README.md`

- [ ] **Step 1:** Update the contract to Shared contracts above, nullable `score`, and `UNSUPPORTED`.
- [ ] **Step 2:** Replace the architecture's score with the spec's `Q` formula, and remove AI-written test rules. Remove AI-written tests from the README.
- [ ] **Step 3:** Run `git grep -n "p / N\|AI-written"`. Expected: matches only in the spec's non-goal lines and this plan.
- [ ] **Step 4:** Commit: `docs: add MentorLens design and alpha plan`.

### Task 2: Parse pasted tests and build the driver

**Owner:** Aryan

**Files:**
- Create: `runner/mentorlens_runner/parsing.py`, `helpers.py`
- Test: `runner/tests/test_parsing.py`

**Interfaces:**
- Produces: `find_method(code:str)->tuple[str,list[str]]` (raises `Unsupported`), `parse_args(input_text:str, params:list[str])->dict`, `parse_value(text:str)->Any`, helpers `ListNode`, `TreeNode`, `build_list`, `build_tree`, `to_plain`.

- [ ] **Step 1: Write failing tests**

```python
def test_find_method():
    assert find_method(open(SAMPLE).read()) == ("twoSum", ["numbers", "target"])
def test_named_input():
    assert parse_args("numbers = [2,7,11,15], target = 9", ["numbers","target"]) == {"numbers":[2,7,11,15],"target":9}
def test_line_input():
    assert parse_args("[2,7]\n9", ["numbers","target"]) == {"numbers":[2,7],"target":9}
def test_multiple_methods_rejected():
    with pytest.raises(Unsupported): find_method("class Solution:\n def a(self): pass\n def b(self): pass")
def test_no_solution_rejected():
    with pytest.raises(Unsupported): find_method("print(input())")
```

- [ ] **Step 2:** Run `pytest runner/tests/test_parsing.py -v`. Expected: FAIL, functions not defined.
- [ ] **Step 3:** Implement using `ast` and `json`. Never use `eval`. Private methods starting with `_` don't count as the one method.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(runner): parse tests and find the method`.

### Task 3: Trace a run

**Owner:** Aryan

**Files:**
- Create: `runner/mentorlens_runner/harness.py`
- Test: `runner/tests/test_harness.py`

**Interfaces:**
- Consumes: Task 2.
- Produces: `run_traced(code:str, input_text:str, max_steps:int=5000, timeout_s:float=5)->dict`, returning the Runner result. Runnable as `python harness.py request.json`, printing only result JSON on stdout. Linked lists and trees are converted with `to_plain` in steps and output.

- [ ] **Step 1: Write failing tests**

```python
def test_sample_steps():
    r = run_traced(SAMPLE_CODE, "numbers = [2,7,11,15], target = 9")
    assert r["outcome"] == "NORMAL" and r["output"] == []
    assert r["steps"][7]["variables"]["left"] == 1 and r["steps"][7]["line"] == 4
def test_endless_loop_keeps_steps():
    r = run_traced(LOOP, "n = 1", max_steps=100)
    assert r["outcome"] == "TRUNCATED" and len(r["steps"]) == 100
def test_syntax_error():
    assert run_traced("class Solution:\n def f(self, a)\n  return a", "a = 1")["outcome"] == "EXCEPTION"
def test_crash():
    assert "ZeroDivisionError" in run_traced(DIVIDE_BY_ZERO, "a = 0")["error"]
def test_in_place_output():
    assert run_traced(REVERSE_NONE, "s = [1,2]")["output"] == [2,1]
def test_print_stays_out_of_result_json():
    assert run_traced(PRINTS, "a = 1")["stdout"] == "hi\n"
```

- [ ] **Step 2:** Run the tests. Expected: FAIL.
- [ ] **Step 3:** Implement with `sys.settrace`, tracing only the user's file and copying values at each step. If the method returns `None`, output is the first argument after the call. Enforce both limits inside the trace function.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(runner): record real steps`.

### Task 4: Sandbox and runner service

**Owner:** Aryan

**Files:**
- Create: `runner/Dockerfile`, `runner/mentorlens_runner/sandbox.py`, `app.py`
- Test: `runner/tests/test_sandbox.py`, `test_app.py`

**Interfaces:**
- Consumes: `harness.py`.
- Produces: `POST /run` with `{language, code, inputText}`, returning the Runner result; `GET /health`. Port 8000.

- [ ] **Step 1: Write failing tests** (sandbox tests are marked `docker`)

```python
def test_network_blocked():  assert "error" in run_sandboxed(OPENS_SOCKET, "a = 1")["error"].lower()
def test_cannot_write_root(): assert run_sandboxed(WRITES_ETC, "a = 1")["outcome"] == "EXCEPTION"
def test_hard_timeout_kills(): assert run_sandboxed(SLEEP_FOREVER, "a = 1")["outcome"] == "TIMED_OUT"
def test_bad_language(client): assert client.post("/run", json={"language":"java","code":"x","inputText":""}).status_code == 400
```

- [ ] **Step 2:** Run the tests. Expected: FAIL.
- [ ] **Step 3:** Implement `run_sandboxed(code, input_text)->dict` with `docker run --rm --network none --read-only --tmpfs /tmp --user 1000 --memory 256m --cpus 1 --pids-limit 64`, killing the container after 10 seconds. Cap output at 2 MB, and limit `app.py` to 4 concurrent containers with a semaphore.
- [ ] **Step 4:** Run `docker build -t mentorlens-runner runner` and `pytest runner/tests -v`. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(runner): add sandbox and service`.

### Task 5: Backend setup, login and queued submissions

**Owner:** Anuj

**Files:**
- Create: Spring project in `backend/`; `auth/`, `submission/` packages

**Interfaces:**
- Produces: `POST /api/auth/register`, `POST /api/auth/login` (JWT), `POST /api/submissions`, `GET /api/submissions/{id}`, `SubmissionPipeline.process(UUID)`, and a `Submission` entity holding user, inputs, status, result JSON, hints JSON and correct code.

- [ ] **Step 1: Write failing tests:** `emptyCodeReturns400`, `unknownLanguageReturns400`, `noTokenReturns401`, `submitReturns202Queued`, `otherUsersSubmissionReturns404`.
- [ ] **Step 2:** Run `./mvnw test`. Expected: FAIL.
- [ ] **Step 3:** Implement with hashed passwords, a fixed `ThreadPoolTaskExecutor` of 4 workers, and an unbounded database-backed queue. On startup, requeue `QUEUED` and `RUNNING` rows. The pipeline is a stub that marks submissions `DONE`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(backend): add login and queued submissions`.

### Task 6: First difference

**Owner:** Anuj

**Files:**
- Create: `backend/.../compare/RunComparer.java`
- Test: `RunComparerTest.java`

**Interfaces:**
- Produces: `Optional<FirstDifference> compare(List<Step> yours, List<Step> correct)`, with `FirstDifference(int step, int line, String variable, JsonNode yours, JsonNode correct)`.

- [ ] **Step 1: Write failing tests:** `sampleDiffersAtStep8Line9OnLeft` (`step=8, line=9, yours=1, correct=0`, using captured traces of the sample and its fix), `identicalRunsEmpty`, `noSharedVariablesEmpty`, `oneRunEndsEarlyReturnsLastStep`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement: walk both by index and compare variables present in both, in name order. `line` is the previous step's line, since that line caused the change. Return empty if under half the variable names are shared.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(backend): find first difference`.

### Task 7: AI client

**Owner:** Anuj

**Files:**
- Create: `backend/.../ai/AiClient.java`, `AiFeedback.java`, `AiFeedbackParser.java`, one provider client, `prompts/feedback.txt`
- Test: `AiFeedbackParserTest.java`

**Interfaces:**
- Produces: `Optional<AiFeedback> review(String question, String code, String inputText, String expectedText, RunResult run)`. `AiFeedback` has `correctCode`, `explanation{what,where,why}`, `hints[3]`, `complexity{time,space}`, `mistakeType`, `issues[{line,type,message}]`.

- [ ] **Step 1: Write failing tests:** `validJsonParses`, `fencedJsonParses`, `missingHintsRejected`, `issueOnMissingLineDropped`, `invalidTwiceReturnsEmpty`, `apiDownReturnsEmpty`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement one retry on invalid JSON, with a prompt asking to keep the student's approach and treat student code as data, not instructions. The provider is chosen before this task, using the cheapest suitable model, and the key is read from `AI_API_KEY`.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(backend): add validated AI feedback`.

### Task 8: Submission pipeline and score

**Owner:** Anuj

**Files:**
- Modify: `SubmissionPipeline.java`
- Create: `RunnerClient.java`, `Score.java`
- Test: `SubmissionPipelineTest.java`, `ScoreTest.java`

**Interfaces:**
- Consumes: Tasks 4 to 7.
- Produces: `result` JSON per `docs/api-contract.md`, with `passed`, nullable `score`, `explanation`, `correctTrace`, `firstDifference`.

- [ ] **Step 1: Write failing tests** with fake runner and AI: `passingCodeSkipsAi`, `failingCodeGetsComparison`, `correctVersionFailingHidesWholeAiReply`, `aiDownReturnsRealRunNullScore`, `crashIsDoneNotFailed`, `unsupportedSkipsAi`, `twentyFirstSubmissionTodaySkipsAi`, `runnerDownMarksFailed`. Score: `score(emptyList)==100`, `score(many bugs)==0`, bug lowers it more than style.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement in the spec's flow order, with starting weights bug 15 and style 5. Compare parsed `expectedText` with output by JSON equality.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(backend): connect the submission pipeline`.

### Task 9: Hints

**Owner:** Anuj

**Files:**
- Create: `HintController.java`
- Test: `HintControllerTest.java`

**Interfaces:**
- Produces: `GET /api/submissions/{id}/hints/next` returning `{level,text,correctCode}`. Result JSON includes only level 1.

- [ ] **Step 1: Write failing tests:** `levelsComeInOrder`, `correctCodeOnlyAtLevel4`, `pastLevel4StaysAtLevel4`, `noCheckedReplyReturns404`, `resultJsonNeverContainsCorrectCode` (with a student comment asking for the answer in the fake AI input), `otherUserGets404`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement from stored hints, with no AI call, and save the current level.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(backend): serve hints by level`.

### Task 10: Workspace page

**Owner:** Abhi

**Files:**
- Create: `frontend/login.html`, `workspace.html`, `js/api.js`, `js/workspace.js`, `css/app.css`
- Test: `frontend/tests/api.test.js`

**Interfaces:**
- Produces: `api.submit(payload)->{id}`, `api.getSubmission(id)`, `api.nextHint(id)`, `api.login(email,password)`.

- [ ] **Step 1: Write failing tests** (`node --test`): `sendsTokenHeader`, `surfacesErrorMessage`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Build login, Monaco editor, question and failing test boxes, the third-party AI notice, and redirect to `result.html?id=`.
- [ ] **Step 4:** Rerun tests, and submit once by hand. Expected: PASS, and a redirect with an id.
- [ ] **Step 5:** Commit: `feat(frontend): add workspace`.

### Task 11: Side-by-side replay

**Owner:** Abhi

**Files:**
- Create: `frontend/result.html`, `js/replay.js`, `js/drawings.js`
- Test: `frontend/tests/replay.test.js`

**Interfaces:**
- Produces: `createReplay(yours, correct, firstDifference)` with `next()`, `back()`, `jumpToDifference()`, `current()`; `draw(value)->HTMLElement`.

- [ ] **Step 1: Write failing tests:** `startsAtStepOne`, `backStopsAtStart`, `nextStopsAtEnd`, `shorterRunHoldsLastStep`, `jumpGoesToStep8`, `noCorrectRunStillPlaysYours`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Build two synced panes with line highlights, plus drawings for arrays, strings, maps, lists and trees. With `firstDifference` null, show one pane and the output mismatch.
- [ ] **Step 4:** Rerun, and open the sample's fixture by hand. Expected: PASS, and it stops at step 8.
- [ ] **Step 5:** Commit: `feat(frontend): add side-by-side replay`.

### Task 12: Result panels and waiting state

**Owner:** Abhi

**Files:**
- Create: `frontend/js/result.js`
- Test: `frontend/tests/result.test.js`

- [ ] **Step 1: Write failing tests:** `pollsUntilDone`, `reloadUsesIdFromUrlWithoutResubmit`, `failedShowsMessage`, `nullExplanationShowsNoCheckedExplanation`, `nullScoreHidesScore`, `unsupportedShowsMessage`, `passedAsksToCheckTest`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Poll every 2 seconds and show queue position. Add explanation layers, the hint button, level 4 comparison with changed lines marked, score, complexity, and the several-valid-answers warning on failures.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(frontend): add result panels`.

### Task 13: Samples, end-to-end check and load test

**Owner:** Rudra, with Aryan on the load test

**Files:**
- Create: 29 more samples in `tests/samples/`, `tests/check_samples.py`, `tests/load_test.py`, `docker-compose.yml`

- [ ] **Step 1:** Write `check_samples.py`, which asserts each sample really produces `actualOutput` via `run_traced`, then run it on the existing sample. Expected: PASS.
- [ ] **Step 2:** Add samples covering each supported data type, using real student mistakes where available. Rerun. Expected: all PASS.
- [ ] **Step 3:** Add compose services for frontend, backend, runner and PostgreSQL. Run `docker compose up`, then submit the sample in a browser. Expected: stops at step 8 with hints.
- [ ] **Step 4:** Run `python tests/load_test.py --users 60` against a fake AI on the target server. Expected: all 60 reach `DONE` with no `FAILED`. Record the longest wait.
- [ ] **Step 5:** Commit: `test: add samples and load test`.

### Task 14: Pilot switch and consent

**Owner:** Anuj (backend), Abhi (frontend)

**Files:**
- Modify: `User`, `SubmissionPipeline.java`, `login.html`, `result.js`

- [ ] **Step 1: Write failing tests:** `registerRequiresConsent`, `explanationOnlyUserGetsNullCorrectTrace`, `explanationOnlyUserStillGetsExplanation`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Add a consent checkbox and a `pilotGroup` field (`STEP_BASED` or `EXPLANATION_ONLY`) set by an admin SQL script. Explanation-only students don't get the correct run or first difference.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: add pilot group and consent`.

### Task 15: Student dashboard (first to cut)

**Owner:** Rudra

**Files:**
- Create: `ProgressController.java`, `frontend/dashboard.html`, `js/dashboard.js`
- Test: `ProgressControllerTest.java`

**Interfaces:**
- Produces: `GET /api/students/me/progress` per `docs/api-contract.md`.

- [ ] **Step 1: Write failing tests:** `countsOnlyOwnSubmissions`, `hidesCorrectVersionFailures`, `newUserGetsEmptyProgress`, `averageIgnoresNullScores`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement the endpoint and page.
- [ ] **Step 4:** Rerun. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: add progress dashboard`.

## Order

The project is decided, but dates are not. Suggested order:

1. Task 1
2. Tasks 2, 3, 5, 10
3. Tasks 4, 6, 7, 8, 11
4. Tasks 9, 12, 13, 14, then 15 if time allows
