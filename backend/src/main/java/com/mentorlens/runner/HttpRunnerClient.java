package com.mentorlens.runner;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpRunnerClient implements RunnerClient {
    private static final Set<String> OUTCOMES = Set.of("NORMAL", "EXCEPTION", "TIMED_OUT", "TRUNCATED", "UNSUPPORTED");

    private final RestClient http;
    private final String url;

    @Autowired
    public HttpRunnerClient(@Value("${app.runner.url:http://localhost:8000}") String url) {
        this(timed(), url);
    }

    HttpRunnerClient(RestClient http, String url) {
        this.http = http;
        this.url = url;
    }

    private static RestClient timed() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(f).build();
    }

    @Override
    public RunResult run(String code, String inputText) {
        RunResult r = http.post().uri(url + "/run")
                .body(Map.of("language", "python", "code", code, "inputText", inputText))
                .retrieve().body(RunResult.class);
        if (r == null || r.outcome() == null || !OUTCOMES.contains(r.outcome())) {
            throw new IllegalStateException("Runner reply has no valid outcome");
        }
        return r.steps() != null ? r : new RunResult(r.outcome(), r.output(), r.stdout(), r.error(), List.of());
    }
}
