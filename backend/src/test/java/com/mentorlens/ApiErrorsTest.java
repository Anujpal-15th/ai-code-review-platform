package com.mentorlens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mentorlens.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:apierrors;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ApiErrorsTest {
    private static final String LOGIN = "{\"email\":\"a@x.com\",\"password\":\"password123\"}";

    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor ip(String addr) {
        return r -> {
            r.setRemoteAddr(addr);
            return r;
        };
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content(LOGIN))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(get("/api/auth/login"))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{nope"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void anythingElseIsGeneric500AndLogged(CapturedOutput out) throws Exception {
        when(users.findByEmail(any())).thenThrow(new IllegalStateException("secret db detail"));
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret db detail");
        assertThat(out.getAll()).contains("secret db detail");
    }

    @Test
    void errorPathIsPermitted() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isInternalServerError());
    }

    @Test
    void nulInCredentialsIs400() throws Exception {
        for (String path : new String[] {"login", "register"}) {
            mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@x.com\",\"password\":\"password\\u0000123\",\"consent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
            mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a\\u0000@x.com\",\"password\":\"password123\",\"consent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        }
    }

    @Test
    void preflightFromAllowedOriginGetsHeader() throws Exception {
        mvc.perform(options("/api/submissions").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void otherOriginAndOtherMethodGetNoHeader() throws Exception {
        mvc.perform(options("/api/submissions").header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/submissions").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "DELETE"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void oversizedBodyIs413WithCorsHeaderBeforeAuth() throws Exception {
        mvc.perform(post("/api/submissions").header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content("x".repeat(256 * 1024 + 1)))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.error").isNotEmpty())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void authRateLimitAppliesPerIpOnAuthPathsOnly() throws Exception {
        for (int i = 0; i < 120; i++) {
            mvc.perform(post("/api/auth/login").with(ip("10.0.0.1"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").with(ip("10.0.0.1"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(post("/api/auth/login").with(ip("10.0.0.2"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/submissions").with(ip("10.0.0.1")))
            .andExpect(status().isUnauthorized());
    }
}
