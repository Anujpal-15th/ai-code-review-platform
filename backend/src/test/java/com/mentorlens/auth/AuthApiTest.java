package com.mentorlens.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired MockMvc mvc;

    ResultActions call(String path, String email, String pw) throws Exception {
        return mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}"));
    }

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        call("register", "u1@x.com", "password123").andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        call("login", "u1@x.com", "password123").andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void invalidEmailAndDuplicateReturn400() throws Exception {
        call("register", "nope", "password123").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        call("register", "u2@x.com", "password123").andExpect(status().isOk());
        call("register", "U2@x.com", "password123").andExpect(status().isBadRequest());
    }

    @Test
    void badLoginIs401WithSameMessage() throws Exception {
        call("register", "u3@x.com", "password123").andExpect(status().isOk());
        String a = call("login", "u3@x.com", "wrongpass1").andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String b = call("login", "ghost@x.com", "password123").andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        if (!a.equals(b)) throw new AssertionError(a + " vs " + b);
    }
}
