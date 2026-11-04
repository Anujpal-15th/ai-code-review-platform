package com.mentorlens.ai;

import com.mentorlens.compare.Step;
import com.mentorlens.runner.RunResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiAiClient implements AiClient {
    static final int MAX_STEPS = 40;
    static final int MAX_TEXT = 2000;
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String INSTRUCTIONS = load();

    private final RestClient http;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiAiClient(@Value("${AI_API_KEY:}") String apiKey,
                          @Value("${app.ai.model:gemini-flash-latest}") String model) {
        this(timed(), apiKey, model);
    }

    GeminiAiClient(RestClient http, String apiKey, String model) {
        this.http = http;
        this.apiKey = apiKey;
        this.model = model;
    }

    private static RestClient timed() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(f).build();
    }

    @Override
    public Optional<AiFeedback> review(String question, String code, String inputText, String expectedText,
                                       RunResult run) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        String prompt = buildPrompt(question, code, inputText, expectedText, run);
        // one retry, only on an invalid reply; an API failure returns empty at once
        for (int attempt = 0; attempt < 2; attempt++) {
            String text;
            try {
                text = call(prompt);
            } catch (RuntimeException e) {
                return Optional.empty(); // message may echo the request, so it is not logged
            }
            Optional<AiFeedback> fb = AiFeedbackParser.parse(text);
            if (fb.isPresent()) return fb;
        }
        return Optional.empty();
    }

    private String call(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
        JsonNode resp = http.post().uri(URL.formatted(model))
                .header("x-goog-api-key", apiKey)
                .body(body)
                .retrieve().body(JsonNode.class);
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : resp.path("candidates").path(0).path("content").path("parts")) {
            sb.append(p.path("text").asString(""));
        }
        return sb.toString();
    }

    /** Instructions first, then untrusted material only inside delimited data sections. */
    static String buildPrompt(String question, String code, String inputText, String expectedText, RunResult run) {
        StringBuilder sb = new StringBuilder(INSTRUCTIONS).append('\n');
        section(sb, "QUESTION", question);
        section(sb, "STUDENT_CODE", code);
        section(sb, "TEST_INPUT", inputText);
        section(sb, "EXPECTED_OUTPUT", expectedText);
        section(sb, "RUN_OUTCOME", run.outcome());
        section(sb, "RUN_OUTPUT", String.valueOf(run.output()));
        section(sb, "RUN_ERROR", run.error());
        List<Step> steps = run.steps() == null ? List.of() : run.steps();
        StringBuilder t = new StringBuilder();
        for (Step s : steps.subList(0, Math.min(steps.size(), MAX_STEPS))) {
            t.append(s.step()).append(" line ").append(s.line()).append(' ').append(s.event()).append(' ')
                    .append(s.variables()).append('\n');
        }
        if (steps.size() > MAX_STEPS) t.append("... ").append(steps.size() - MAX_STEPS).append(" more steps omitted\n");
        section(sb, "RUN_TRACE", t.toString());
        return sb.toString();
    }

    private static void section(StringBuilder sb, String name, String text) {
        String s = text == null ? "" : text;
        if (s.length() > MAX_TEXT) s = s.substring(0, MAX_TEXT) + "...[truncated]";
        // stop untrusted text from forging a delimiter
        s = s.replace("<<<", "< < <").replace(">>>", "> > >");
        sb.append("<<<").append(name).append(">>>\n").append(s).append("\n<<<END_").append(name).append(">>>\n");
    }

    private static String load() {
        try (var in = new ClassPathResource("prompts/feedback.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
