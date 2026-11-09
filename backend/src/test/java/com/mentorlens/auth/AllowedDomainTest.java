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

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:domain;DB_CLOSE_DELAY=-1",
    "ALLOWED_EMAIL_DOMAIN=Uni.edu"})
@AutoConfigureMockMvc
class AllowedDomainTest {
    @Autowired MockMvc mvc;

    ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"consent\":true}"));
    }

    @Test
    void onlyTheAllowedDomainRegisters() throws Exception {
        register("a@uni.edu").andExpect(status().isOk());
        register("b@UNI.edu").andExpect(status().isOk());
        register("c@other.com").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        register("d@notuni.edu").andExpect(status().isBadRequest());
        register("e@uni.edu.evil.com").andExpect(status().isBadRequest());
    }
}
