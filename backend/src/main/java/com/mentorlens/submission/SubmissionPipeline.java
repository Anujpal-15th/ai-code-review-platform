package com.mentorlens.submission;

import com.mentorlens.ai.AiClient;
import com.mentorlens.ai.AiFeedback;
import com.mentorlens.compare.RunComparer;
import com.mentorlens.runner.RunResult;
import com.mentorlens.runner.RunnerClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Runs the student's code, then checks any AI reply by running its correct version. */
@Component
public class SubmissionPipeline {
    static final int DAILY_AI_LIMIT = 20;
    private static final String RUNNER_DOWN = "The code runner is unavailable right now. Please try again later.";
    // trailing tokens: without this, "1 apple" would parse as the number 1. Single quotes: Python-style expected text.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES).build();
    // matches quoted strings first, so a True inside a string is left alone
    private static final Pattern PYTHON_WORDS = Pattern.compile(
            "'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|\\b(True|False|None)\\b");

    private final SubmissionRepository submissions;
    private final RunnerClient runner;
    private final AiClient ai;
    private final RunComparer comparer = new RunComparer();

    SubmissionPipeline(SubmissionRepository submissions, RunnerClient runner, AiClient ai) {
        this.submissions = submissions;
        this.runner = runner;
        this.ai = ai;
    }

    public void process(UUID id) {
        Submission s = submissions.findById(id).orElseThrow();
        String input = s.getFailingInput();
        JsonNode expected = parseExpected(s.getFailingExpected());
        RunResult run = run(s.getCode(), input);
        boolean passed = isCorrect(run, expected);
        ObjectNode result = realRun(run, passed, expected);

        if (!passed && !"UNSUPPORTED".equals(run.outcome()) && underDailyLimit(id)) {
            // saved before the call so a crash or a concurrent worker still sees this use
            s.setAiUsedAt(Instant.now());
            submissions.save(s);
            Optional<AiFeedback> reply = ai.review(s.getQuestion(), s.getCode(), input, s.getFailingExpected(), run);
            if (reply.isPresent()) {
                AiFeedback fb = reply.get();
                RunResult correct = run(fb.correctCode(), input);
                if (isCorrect(correct, expected)) {
                    addFeedback(result, fb, run, correct);
                    if (submissions.isExplanationOnly(id)) { // pilot: no correct run or first difference
                        result.putNull("correctTrace");
                        result.putNull("firstDifference");
                    }
                    s.setCorrectCode(fb.correctCode());
                    s.setHintsJson(MAPPER.writeValueAsString(fb.hints()));
                    s.setHintLevel(1); // the result already shows hint 1
                } else {
                    result.put("noCheckedExplanation", true);
                }
            }
        }

        s.setResultJson(MAPPER.writeValueAsString(result));
        s.setStatus(SubmissionStatus.DONE);
        submissions.save(s);
    }

    private RunResult run(String code, String input) {
        try {
            return runner.run(code, input);
        } catch (RuntimeException e) {
            throw new PipelineException(RUNNER_DOWN, e);
        }
    }

    // ponytail: check-then-call, so concurrent workers may overshoot by up to 3; a per-student lock if cost matters
    private boolean underDailyLimit(UUID id) {
        return submissions.countAiUsesBySameStudent(id, Instant.now().truncatedTo(ChronoUnit.DAYS)) < DAILY_AI_LIMIT;
    }

    private static JsonNode parseExpected(String text) {
        try {
            JsonNode node = MAPPER.readTree(fromPython(text));
            return node.isMissingNode() ? MAPPER.getNodeFactory().stringNode(text) : node;
        } catch (JacksonException e) {
            return MAPPER.getNodeFactory().stringNode(text);
        }
    }

    private static String fromPython(String text) {
        return PYTHON_WORDS.matcher(text).replaceAll(m -> Matcher.quoteReplacement(switch (String.valueOf(m.group(1))) {
            case "True" -> "true";
            case "False" -> "false";
            case "None" -> "null";
            default -> m.group();
        }));
    }

    private static boolean isCorrect(RunResult run, JsonNode expected) {
        return "NORMAL".equals(run.outcome()) && expected.equals(run.output());
    }

    /** All AI fields start null, which is the whole result when no checked reply is available. */
    private static ObjectNode realRun(RunResult run, boolean passed, JsonNode expected) {
        ObjectNode r = MAPPER.createObjectNode();
        r.put("outcome", run.outcome());
        r.put("error", run.error());
        r.put("passed", passed);
        r.set("actualOutput", MAPPER.valueToTree(run.output()));
        r.set("expectedOutput", expected);
        r.set("studentTrace", MAPPER.valueToTree(run.steps()));
        for (String field : new String[] {"correctTrace", "firstDifference", "explanation", "mistakeType",
                "complexity", "score", "hint"}) {
            r.putNull(field);
        }
        return r;
    }

    private void addFeedback(ObjectNode r, AiFeedback fb, RunResult run, RunResult correct) {
        r.set("correctTrace", MAPPER.valueToTree(correct.steps()));
        r.set("firstDifference", MAPPER.valueToTree(comparer.compare(run.steps(), correct.steps()).orElse(null)));
        r.set("explanation", MAPPER.valueToTree(fb.explanation()));
        r.put("mistakeType", fb.mistakeType());
        r.set("complexity", MAPPER.valueToTree(fb.complexity()));
        r.put("score", Score.score(fb.issues()));
        r.set("hint", MAPPER.valueToTree(Map.of("level", 1, "text", fb.hints().get(0))));
    }
}
