package com.mentorlens.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mentorlens.ai.AiClient;
import com.mentorlens.ai.AiFeedback;
import com.mentorlens.auth.PilotGroup;
import com.mentorlens.auth.User;
import com.mentorlens.auth.UserRepository;
import com.mentorlens.compare.Step;
import com.mentorlens.runner.RunResult;
import com.mentorlens.runner.RunnerClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Own database: rows other test contexts left behind must not reach these mocks at startup.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pipeline;DB_CLOSE_DELAY=-1")
class SubmissionPipelineTest {
    private static final JsonMapper M = new JsonMapper();
    private static final String STUDENT = "student code";
    private static final String CORRECT = "SECRET correct code";
    private static final String IN = "numbers = [2,7], target = 9";

    @Autowired SubmissionPipeline pipeline;
    @Autowired SubmissionRepository submissions;
    @Autowired UserRepository users;
    @Autowired SubmissionQueue queue;
    @MockitoBean RunnerClient runner;
    @MockitoBean AiClient ai;

    private User user;

    @BeforeEach
    void newStudent() {
        user = users.save(new User(UUID.randomUUID() + "@x.com", "hash"));
    }

    private static List<Step> steps(int left) {
        return List.of(new Step(1, 1, "call", "f", json("{\"left\":0}")),
                new Step(2, 2, "line", "f", json("{\"left\":" + left + "}")));
    }

    private static JsonNode json(String s) {
        return M.readTree(s);
    }

    private static RunResult run(String outcome, String output, List<Step> steps) {
        return new RunResult(outcome, json(output), "", null, steps);
    }

    private static AiFeedback feedback() {
        return new AiFeedback(CORRECT, new AiFeedback.Explanation("w", "wh", "y"),
                List.of("hint one", "hint two", "hint three"), new AiFeedback.Complexity("O(n)", "O(1)"),
                "wrong-pointer-moved",
                List.of(new AiFeedback.Issue(1, "bug", "m"), new AiFeedback.Issue(1, "style", "m")));
    }

    private Submission submit(String expected) {
        return submissions.save(new Submission(user, "python", "q", STUDENT, IN, expected));
    }

    private Submission process(String expected) {
        Submission s = submit(expected);
        pipeline.process(s.getId());
        return submissions.findById(s.getId()).orElseThrow();
    }

    private static JsonNode result(Submission s) {
        return M.readTree(s.resultJson);
    }

    private void studentFailsAndAiReplies() {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[]", steps(1)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.of(feedback()));
    }

    private static void assertNoAiFields(JsonNode r) {
        for (String f : List.of("explanation", "score", "hint", "mistakeType", "complexity", "correctTrace",
                "firstDifference")) {
            assertThat(r.has(f)).as(f + " present").isTrue();
            assertThat(r.get(f).isNull()).as(f + " null").isTrue();
        }
    }

    private static void assertNothingStored(Submission s) {
        assertThat(s.correctCode).isNull();
        assertThat(s.hintsJson).isNull();
    }

    @Test
    void passingCodeSkipsAi() {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[1,2]", steps(0)));

        Submission s = process("[1,2]");

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(result(s).get("passed").asBoolean()).isTrue();
        assertThat(result(s).get("error").isNull()).isTrue();
        assertThat(result(s).get("studentTrace")).hasSize(2);
        assertNoAiFields(result(s));
        assertThat(s.aiUsedAt).isNull();
        verify(ai, never()).review(any(), any(), any(), any(), any());
    }

    @Test
    void expectedMatchesRegardlessOfSpacing() {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[1, 2]", steps(0)));
        assertThat(result(process("[1, 2]")).get("passed").asBoolean()).isTrue();
        assertThat(result(process("[1,2]")).get("passed").asBoolean()).isTrue();
        assertThat(result(process("  [ 1 ,2 ]\n")).get("passed").asBoolean()).isTrue();
    }

    @Test
    void expectedThatIsNotJsonIsPlainString() {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "\"abc def\"", steps(0)));
        Submission s = process("abc def");
        assertThat(result(s).get("passed").asBoolean()).isTrue();
        assertThat(result(s).get("expectedOutput").asString()).isEqualTo("abc def");
    }

    private boolean passes(String output, String expected) {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", output, steps(0)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        return result(process(expected)).get("passed").asBoolean();
    }

    @Test
    void pythonStyleExpectedMatches() {
        assertThat(passes("true", "True")).isTrue();
        assertThat(passes("false", "False")).isTrue();
        assertThat(passes("null", "None")).isTrue();
        assertThat(passes("[\"a\",\"b\"]", "['a','b']")).isTrue();
        assertThat(passes("{\"a\":null,\"b\":[true]}", "{'a': None, 'b': [True]}")).isTrue();
    }

    @Test
    void pythonWordsInsideStringsAreLeftAlone() {
        assertThat(passes("[\"True\"]", "['True']")).isTrue();
        assertThat(passes("\"None\"", "\"None\"")).isTrue();
        assertThat(passes("[true]", "['True']")).isFalse();
    }

    @Test
    void otherTextStaysPlainString() {
        assertThat(passes("1", "1 apple")).isFalse();
        assertThat(passes("true", "True story")).isFalse();
        assertThat(passes("\"True story\"", "True story")).isTrue();
        assertThat(passes("\"it's\"", "it's")).isTrue();
    }

    @Test
    void expectedWithTrailingTextIsPlainStringNotItsFirstToken() {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "1", steps(0)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        Submission s = process("1 apple");
        assertThat(result(s).get("passed").asBoolean()).isFalse();
    }

    @Test
    void failingCodeGetsComparison() {
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[1,2]", steps(0)));

        Submission s = process("[1,2]");
        JsonNode r = result(s);

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(r.get("outcome").asString()).isEqualTo("NORMAL");
        assertThat(r.get("passed").asBoolean()).isFalse();
        assertThat(r.get("actualOutput").toString()).isEqualTo("[]");
        assertThat(r.get("expectedOutput").toString()).isEqualTo("[1,2]");
        assertThat(r.get("studentTrace").get(1).get("variables").get("left").asInt()).isEqualTo(1);
        assertThat(r.get("correctTrace").get(1).get("variables").get("left").asInt()).isEqualTo(0);
        assertThat(r.get("firstDifference").get("step").asInt()).isEqualTo(2);
        assertThat(r.get("firstDifference").get("variable").asString()).isEqualTo("left");
        assertThat(r.get("explanation").get("what").asString()).isEqualTo("w");
        assertThat(r.get("mistakeType").asString()).isEqualTo("wrong-pointer-moved");
        assertThat(r.get("complexity").get("time").asString()).isEqualTo("O(n)");
        assertThat(r.get("score").asInt()).isEqualTo(80);
        assertThat(r.get("hint").get("level").asInt()).isEqualTo(1);
        assertThat(r.get("hint").get("text").asString()).isEqualTo("hint one");
        assertThat(r.has("noCheckedExplanation")).isFalse();
    }

    private void explanationOnlyStudent() {
        user.setPilotGroup(PilotGroup.EXPLANATION_ONLY);
        user = users.save(user);
    }

    @Test
    void explanationOnlyUserGetsNullCorrectTrace() {
        explanationOnlyStudent();
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[1,2]", steps(0)));

        JsonNode r = result(process("[1,2]"));

        assertThat(r.has("correctTrace")).isTrue();
        assertThat(r.get("correctTrace").isNull()).isTrue();
        assertThat(r.has("firstDifference")).isTrue();
        assertThat(r.get("firstDifference").isNull()).isTrue();
    }

    @Test
    void explanationOnlyUserStillGetsExplanation() {
        explanationOnlyStudent();
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[1,2]", steps(0)));

        Submission s = process("[1,2]");
        JsonNode r = result(s);

        assertThat(r.get("studentTrace")).hasSize(2);
        assertThat(r.get("explanation").get("what").asString()).isEqualTo("w");
        assertThat(r.get("mistakeType").asString()).isEqualTo("wrong-pointer-moved");
        assertThat(r.get("complexity").get("time").asString()).isEqualTo("O(n)");
        assertThat(r.get("score").asInt()).isEqualTo(80);
        assertThat(r.get("hint").get("text").asString()).isEqualTo("hint one");
        assertThat(r.has("noCheckedExplanation")).isFalse();
        // hints and level 4 correct code are unchanged
        assertThat(s.correctCode).isEqualTo(CORRECT);
        assertThat(s.hintLevel).isEqualTo(1);
        assertThat(M.readTree(s.hintsJson)).hasSize(3);
    }

    @Test
    void explanationOnlyUserStillHasCorrectVersionChecked() {
        explanationOnlyStudent();
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[9]", steps(0)));

        Submission s = process("[1,2]");

        verify(runner).run(CORRECT, IN);
        assertNoAiFields(result(s));
        assertThat(result(s).get("noCheckedExplanation").asBoolean()).isTrue();
        assertNothingStored(s);
    }

    @Test
    void stepBasedAndUnsetUsersKeepCorrectTrace() {
        user.setPilotGroup(PilotGroup.STEP_BASED);
        user = users.save(user);
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[1,2]", steps(0)));

        assertThat(result(process("[1,2]")).get("correctTrace").isNull()).isFalse();
    }

    @Test
    void correctCodeAndLaterHintsAreStoredButNeverInResult() {
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[1,2]", steps(0)));

        Submission s = process("[1,2]");

        assertThat(s.correctCode).isEqualTo(CORRECT);
        assertThat(M.readTree(s.hintsJson).toString()).isEqualTo("[\"hint one\",\"hint two\",\"hint three\"]");
        assertThat(s.resultJson).doesNotContain(CORRECT).doesNotContain("correctCode")
                .doesNotContain("hint two").doesNotContain("hint three");
    }

    @Test
    void aiReceivesTheRealRun() {
        RunResult real = run("EXCEPTION", "null", steps(1));
        when(runner.run(STUDENT, IN)).thenReturn(real);
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        process("[1,2]");

        verify(ai).review("q", STUDENT, IN, "[1,2]", real);
    }

    @Test
    void correctVersionFailingHidesWholeAiReply() {
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[9]", steps(0)));

        Submission s = process("[1,2]");

        assertNoAiFields(result(s));
        assertThat(result(s).get("noCheckedExplanation").asBoolean()).isTrue();
        assertThat(result(s).get("actualOutput").toString()).isEqualTo("[]");
        assertNothingStored(s);
        assertThat(s.resultJson).doesNotContain(CORRECT);
    }

    @Test
    void correctVersionCrashingAlsoHidesAiReply() {
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenReturn(run("EXCEPTION", "[1,2]", steps(0)));

        Submission s = process("[1,2]");

        assertNoAiFields(result(s));
        assertThat(result(s).get("noCheckedExplanation").asBoolean()).isTrue();
        assertNothingStored(s);
    }

    @Test
    void unmatchableRunsKeepCorrectTraceWithoutMarker() {
        studentFailsAndAiReplies();
        List<Step> other = List.of(new Step(1, 1, "call", "f", json("{\"x\":1,\"y\":2,\"z\":3}")));
        when(runner.run(CORRECT, IN)).thenReturn(run("NORMAL", "[1,2]", other));

        JsonNode r = result(process("[1,2]"));

        assertThat(r.get("firstDifference").isNull()).isTrue();
        assertThat(r.get("correctTrace")).hasSize(1);
        assertThat(r.get("score").asInt()).isEqualTo(80);
    }

    @Test
    void aiDownReturnsRealRunNullScore() {
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[]", steps(1)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        Submission s = process("[1,2]");

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertNoAiFields(result(s));
        assertThat(result(s).has("noCheckedExplanation")).isFalse();
        assertThat(result(s).get("studentTrace")).hasSize(2);
        assertNothingStored(s);
        verify(runner, never()).run(eq(CORRECT), any());
    }

    @Test
    void crashIsDoneNotFailed() {
        when(runner.run(STUDENT, IN)).thenReturn(new RunResult("EXCEPTION", null, "", "boom", steps(1)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        Submission s = process("[1,2]");

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(result(s).get("outcome").asString()).isEqualTo("EXCEPTION");
        assertThat(result(s).get("passed").asBoolean()).isFalse();
        assertThat(result(s).get("actualOutput").isNull()).isTrue();
        assertThat(result(s).get("error").asString()).isEqualTo("boom");
    }

    @Test
    void crashWithMatchingOutputIsNotPassed() {
        when(runner.run(STUDENT, IN)).thenReturn(run("TRUNCATED", "[1,2]", steps(1)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        assertThat(result(process("[1,2]")).get("passed").asBoolean()).isFalse();
    }

    @Test
    void unsupportedSkipsAi() {
        when(runner.run(STUDENT, IN)).thenReturn(new RunResult("UNSUPPORTED", null, "", null, List.of()));

        Submission s = process("[1,2]");

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(result(s).get("outcome").asString()).isEqualTo("UNSUPPORTED");
        assertThat(result(s).get("passed").asBoolean()).isFalse();
        assertNoAiFields(result(s));
        verify(ai, never()).review(any(), any(), any(), any(), any());
    }

    private void usedAi(int times, Instant at) {
        for (int i = 0; i < times; i++) {
            Submission s = submit("[1,2]");
            s.setStatus(SubmissionStatus.DONE);
            s.setAiUsedAt(at);
            submissions.save(s);
        }
    }

    @Test
    void twentyFirstSubmissionTodaySkipsAi() {
        usedAi(20, Instant.now());
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[]", steps(1)));

        Submission s = process("[1,2]");

        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertNoAiFields(result(s));
        assertThat(result(s).has("noCheckedExplanation")).isFalse();
        assertThat(s.aiUsedAt).isNull();
        verify(ai, never()).review(any(), any(), any(), any(), any());
    }

    @Test
    void twentiethSubmissionStillGetsAiAndIsCounted() {
        usedAi(19, Instant.now());
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[]", steps(1)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        Submission s = process("[1,2]");

        verify(ai).review(any(), any(), any(), any(), any());
        assertThat(s.aiUsedAt).isNotNull();
    }

    @Test
    void yesterdaysAndOtherStudentsUseDoNotCount() {
        usedAi(20, Instant.now().minus(Duration.ofDays(1)));
        User me = user;
        user = users.save(new User(UUID.randomUUID() + "@x.com", "hash"));
        usedAi(20, Instant.now());
        user = me;
        when(runner.run(STUDENT, IN)).thenReturn(run("NORMAL", "[]", steps(1)));
        when(ai.review(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        process("[1,2]");

        verify(ai).review(any(), any(), any(), any(), any());
    }

    @Test
    void runnerDownMarksFailed() throws Exception {
        when(runner.run(STUDENT, IN)).thenThrow(new IllegalStateException("connection refused to secret-host"));
        Submission s = submit("[1,2]");

        queue.enqueue(s.getId());

        Submission done = awaitFinished(s.getId());
        assertThat(done.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(done.error).contains("runner").doesNotContain("secret-host");
    }

    @Test
    void runnerFailureKeepsCauseButCleanMessage() {
        IllegalStateException cause = new IllegalStateException("connection refused to secret-host");
        when(runner.run(STUDENT, IN)).thenThrow(cause);
        Submission s = submit("[1,2]");

        assertThatThrownBy(() -> pipeline.process(s.getId()))
                .isInstanceOf(PipelineException.class)
                .hasCause(cause)
                .message().doesNotContain("secret-host");
    }

    @Test
    void runnerDownOnCorrectVersionMarksFailed() throws Exception {
        studentFailsAndAiReplies();
        when(runner.run(CORRECT, IN)).thenThrow(new IllegalStateException("down"));
        Submission s = submit("[1,2]");

        queue.enqueue(s.getId());

        assertThat(awaitFinished(s.getId()).getStatus()).isEqualTo(SubmissionStatus.FAILED);
    }

    private Submission awaitFinished(UUID id) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Submission s = submissions.findById(id).orElseThrow();
            if (s.getStatus() == SubmissionStatus.DONE || s.getStatus() == SubmissionStatus.FAILED) {
                return s;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("submission did not finish");
    }
}
