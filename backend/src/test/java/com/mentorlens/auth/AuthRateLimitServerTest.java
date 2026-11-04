package com.mentorlens.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Real Tomcat: MockMvc does not decode the path the way the server does. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:ratelimitserver;DB_CLOSE_DELAY=-1")
class AuthRateLimitServerTest {
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;

    private int login(String rawPath) throws Exception {
        // java.net.URI keeps the raw path, so %61 goes on the wire as written
        URI uri = URI.create("http://localhost:" + port + rawPath);
        assertThat(uri.getRawPath()).isEqualTo(rawPath);
        HttpRequest req = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void encodedPathCannotBypassTheLimit() throws Exception {
        for (int i = 0; i < AuthRateLimitFilter.MAX_PER_WINDOW; i++) {
            assertThat(login("/%61pi/auth/login")).isEqualTo(400); // routed to login, failed validation
        }
        assertThat(login("/%61pi/auth/login")).isEqualTo(429);
        assertThat(login("/api/auth/login")).isEqualTo(429); // same bucket
    }
}
