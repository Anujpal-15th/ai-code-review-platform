package com.mentorlens.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpRunnerClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpRunnerClient client = new HttpRunnerClient(builder.build(), "http://runner:8000");

    @Test
    void postsContractRequestAndReadsResponse() {
        server.expect(requestTo("http://runner:8000/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"language\":\"python\",\"code\":\"c\",\"inputText\":\"i\"}"))
                .andRespond(withSuccess("""
                        {"outcome":"NORMAL","output":[1,2],"stdout":"","error":null,
                         "steps":[{"step":1,"line":2,"event":"call","function":"f","variables":{"a":1}}]}""",
                        MediaType.APPLICATION_JSON));

        RunResult r = client.run("c", "i");

        assertThat(r.outcome()).isEqualTo("NORMAL");
        assertThat(r.output().toString()).isEqualTo("[1,2]");
        assertThat(r.steps()).hasSize(1);
        assertThat(r.steps().get(0).variables().get("a").asInt()).isEqualTo(1);
        server.verify();
    }

    @Test
    void missingStepsBecomeEmptyList() {
        server.expect(requestTo("http://runner:8000/run"))
                .andRespond(withSuccess("{\"outcome\":\"UNSUPPORTED\"}", MediaType.APPLICATION_JSON));
        assertThat(client.run("c", "i").steps()).isEmpty();
    }

    @Test
    void httpErrorThrows() {
        server.expect(requestTo("http://runner:8000/run")).andRespond(withServerError());
        assertThatThrownBy(() -> client.run("c", "i")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unreachableThrows() {
        server.expect(requestTo("http://runner:8000/run")).andRespond(withException(new IOException("refused")));
        assertThatThrownBy(() -> client.run("c", "i")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void replyWithoutOutcomeThrows() {
        server.expect(requestTo("http://runner:8000/run"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.run("c", "i")).isInstanceOf(RuntimeException.class);
    }
}
