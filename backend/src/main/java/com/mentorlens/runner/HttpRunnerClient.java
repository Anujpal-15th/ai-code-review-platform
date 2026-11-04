package com.mentorlens.runner;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpRunnerClient implements RunnerClient {
    private static final Logger log = LoggerFactory.getLogger(HttpRunnerClient.class);
    private static final String UNPARSABLE = "Runner reply could not be parsed";
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

    private static IllegalStateException unparsable() {
        log.error(UNPARSABLE);
        return new IllegalStateException(UNPARSABLE);
    }

    @Override
    public RunResult run(String code, String inputText) {
        RunResult r;
        // exceptions from the reply are replaced, not chained: their messages can quote the body, and the queue logs them
        try {
            r = http.post().uri(url + "/run")
                    .body(Map.of("language", "python", "code", code, "inputText", inputText))
                    .retrieve().body(RunResult.class);
        } catch (RestClientResponseException e) {
            log.error("Runner returned HTTP {}", e.getStatusCode().value());
            throw new IllegalStateException("Runner returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            if (e.contains(IOException.class)) {
                log.error("Runner connection failed: {}", e.getClass().getSimpleName());
                throw e;
            }
            throw unparsable();
        }
        if (r == null || r.outcome() == null || !OUTCOMES.contains(r.outcome())) {
            throw unparsable();
        }
        return r.steps() != null ? r : new RunResult(r.outcome(), r.output(), r.stdout(), r.error(), List.of());
    }
}
