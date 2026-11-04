package com.mentorlens.submission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mentorlens.auth.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.hamcrest.Matchers;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionApiTest {

    @Autowired MockMvc mvc;
    @MockitoBean SubmissionPipeline pipeline;
    @Autowired SubmissionRepository submissions;
    @Autowired UserRepository users;
    @Autowired JwtEncoder encoder;

    Submission row(String email, SubmissionStatus status, Instant createdAt) {
        Submission s = new Submission(users.findByEmail(email).orElseThrow(), "python", "q", "code", "in", "out");
        s.setStatus(status);
        s.createdAt = createdAt;
        return submissions.save(s);
    }

    String tokenWith(String sub, Instant issued, Instant expires) {
        JwtClaimsSet c = JwtClaimsSet.builder().subject(sub).issuedAt(issued).expiresAt(expires).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), c)).getTokenValue();
    }

    static String body(String language, String code) {
        return """
            {"language":"%s","question":"Two sum","code":"%s",
             "failingTest":{"inputText":"numbers = [2,7], target = 9","expectedText":"[1,2]"}}
            """.formatted(language, code);
    }

    String register(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"consent\":true}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(res);
        m.find();
        return "Bearer " + m.group(1);
    }

    String submit(String token) throws Exception {
        String res = mvc.perform(post("/api/submissions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body("python", "class Solution: pass")))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(res);
        m.find();
        return m.group(1);
    }

    @Test
    void emptyCodeReturns400() throws Exception {
        mvc.perform(post("/api/submissions").header("Authorization", register("a1@x.com"))
                .contentType(MediaType.APPLICATION_JSON).content(body("python", "  ")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void unknownLanguageReturns400() throws Exception {
        mvc.perform(post("/api/submissions").header("Authorization", register("a2@x.com"))
                .contentType(MediaType.APPLICATION_JSON).content(body("cobol", "x = 1")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void noTokenReturns401() throws Exception {
        mvc.perform(post("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                .content(body("python", "x = 1")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void submitReturns202Queued() throws Exception {
        submit(register("a3@x.com"));
    }

    @Test
    void otherUsersSubmissionReturns404() throws Exception {
        String id = submit(register("a4@x.com"));
        mvc.perform(get("/api/submissions/" + id).header("Authorization", register("a5@x.com")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/submissions/" + UUID.randomUUID()).header("Authorization", register("a6@x.com")))
            .andExpect(status().isNotFound());
    }

    @Test
    void failingPipelineEndsInFailedWithMessage() throws Exception {
        doThrow(new IllegalStateException("boom")).when(pipeline).process(any());
        String token = register("a7@x.com");
        String id = submit(token);
        for (int i = 0; i < 50; i++) {
            String res = mvc.perform(get("/api/submissions/" + id).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
            if (res.contains("\"status\":\"FAILED\"")) {
                mvc.perform(get("/api/submissions/" + id).header("Authorization", token))
                    .andExpect(jsonPath("$.error").isNotEmpty()).andExpect(jsonPath("$.queuePosition").value(0));
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("never reached FAILED");
    }

    @Test
    void errorInWorkerEndsFailedWithGenericMessage() throws Exception {
        doThrow(new StackOverflowError("secret detail")).when(pipeline).process(any());
        String token = register("d1@x.com");
        String id = submit(token);
        for (int i = 0; i < 50; i++) {
            String res = mvc.perform(get("/api/submissions/" + id).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
            if (res.contains("\"status\":\"FAILED\"")) {
                org.assertj.core.api.Assertions.assertThat(res).contains("Processing failed unexpectedly")
                    .doesNotContain("secret detail");
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("never reached FAILED");
    }

    @Test
    void validTokenForMissingUserIs401() throws Exception {
        Instant now = Instant.now();
        String t = tokenWith(UUID.randomUUID().toString(), now, now.plus(1, ChronoUnit.HOURS));
        mvc.perform(post("/api/submissions").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content(body("python", "x = 1")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void nulInSubmissionFieldsIs400() throws Exception {
        String token = register("d2@x.com");
        String nul = "\\u0000";
        for (String b : new String[] {
                body("python", "x" + nul),
                body("python", "x").replace("Two sum", "Two" + nul),
                body("python", "x").replace("target = 9", "target" + nul),
                body("python", "x").replace("[1,2]", "[1" + nul)}) {
            mvc.perform(post("/api/submissions").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(b))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        }
    }

    @Test
    void fourthActiveSubmissionIs429UntilOneFinishes() throws Exception {
        String token = register("d3@x.com");
        Submission one = submissions.findById(UUID.fromString(submit(token))).orElseThrow();
        submit(token);
        submit(token); // the mocked pipeline leaves them RUNNING
        mvc.perform(post("/api/submissions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body("python", "x = 1")))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").exists());

        one.setStatus(SubmissionStatus.DONE);
        submissions.save(one);
        submit(token);
    }

    @Test
    void parallelSubmitsCannotPassTheLimit() throws Exception {
        String token = register("d4@x.com");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var calls = new java.util.ArrayList<java.util.concurrent.Callable<Integer>>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> mvc.perform(post("/api/submissions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("python", "x = 1")))
                    .andReturn().getResponse().getStatus());
            }
            int accepted = 0;
            for (var f : pool.invokeAll(calls)) if (f.get() == 202) accepted++;
            org.junit.jupiter.api.Assertions.assertEquals(3, accepted);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void malformedIdReturns404WithErrorBody() throws Exception {
        mvc.perform(get("/api/submissions/not-a-uuid").header("Authorization", register("b1@x.com")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Submission not found"));
    }

    @Test
    void doneSubmissionReturnsResultWithoutCorrectCode() throws Exception {
        String token = register("b2@x.com");
        Submission s = row("b2@x.com", SubmissionStatus.DONE, Instant.now());
        s.setResultJson("{\"outcome\":\"UNSUPPORTED\",\"passed\":false}");
        s.setCorrectCode("SECRET");
        submissions.save(s);
        mvc.perform(get("/api/submissions/" + s.getId()).header("Authorization", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.queuePosition").value(0))
            .andExpect(jsonPath("$.result.outcome").value("UNSUPPORTED"))
            .andExpect(jsonPath("$.error").doesNotExist())
            .andExpect(content().string(Matchers.not(Matchers.containsString("SECRET"))));
    }

    @Test
    void queuedSubmissionHasPositionAboveZero() throws Exception {
        String token = register("b3@x.com");
        Submission first = row("b3@x.com", SubmissionStatus.QUEUED, Instant.now().minus(10, ChronoUnit.MINUTES));
        Submission second = row("b3@x.com", SubmissionStatus.QUEUED, Instant.now());
        try {
            mvc.perform(get("/api/submissions/" + first.getId()).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("QUEUED")).andExpect(jsonPath("$.queuePosition").value(1));
            mvc.perform(get("/api/submissions/" + second.getId()).header("Authorization", token))
                .andExpect(jsonPath("$.queuePosition").value(Matchers.greaterThan(1)));
        } finally {
            submissions.delete(first);
            submissions.delete(second);
        }
    }

    @Test
    void expiredAndTamperedTokensReturn401() throws Exception {
        String valid = register("b4@x.com").substring("Bearer ".length());
        String sub = users.findByEmail("b4@x.com").orElseThrow().getId().toString();
        Instant now = Instant.now();
        String expired = tokenWith(sub, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));
        int dot = valid.lastIndexOf('.') + 1;
        String tampered = valid.substring(0, dot) + (valid.charAt(dot) == 'A' ? 'B' : 'A') + valid.substring(dot + 1);
        for (String t : new String[] {expired, tampered}) {
            mvc.perform(get("/api/submissions/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
        }
    }
}
