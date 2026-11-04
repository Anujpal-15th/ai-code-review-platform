package com.mentorlens.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestFiltersTest {
    private static final int LIMIT = 256 * 1024;

    /** A chunked request: the body is there but no length is declared. */
    private static HttpServletRequest chunked(int size) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/submissions");
        r.setContent(new byte[size]);
        return new HttpServletRequestWrapper(r) {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
    }

    @Test
    void wildcardCorsOriginStopsStartup() {
        assertThatThrownBy(() -> new SecurityConfig("x".repeat(32), "http://a.example, *"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chunkedBodyOverLimitIs413() throws Exception {
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new BodyLimitFilter().doFilter(chunked(LIMIT + 1), res, chain);
        assertThat(res.getStatus()).isEqualTo(413);
        assertThat(res.getContentAsString()).contains("\"error\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void chunkedBodyAtLimitPassesWithBodyIntact() throws Exception {
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new BodyLimitFilter().doFilter(chunked(LIMIT), res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(LIMIT);
    }

    @Test
    void rateLimitResetsWhenTheWindowRolls() throws Exception {
        AtomicLong now = new AtomicLong();
        var filter = new AuthRateLimitFilter(now::get);
        for (int i = 0; i < AuthRateLimitFilter.MAX_PER_WINDOW; i++) {
            assertThat(login(filter)).isEqualTo(200);
        }
        assertThat(login(filter)).isEqualTo(429);
        now.addAndGet(60_000);
        assertThat(login(filter)).isEqualTo(200);
    }

    private static int login(AuthRateLimitFilter f) throws Exception {
        var res = new MockHttpServletResponse();
        var req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }
}
