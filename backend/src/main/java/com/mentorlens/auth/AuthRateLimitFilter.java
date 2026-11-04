package com.mentorlens.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP limit on /api/auth/**. The limit is high enough for a lab of 60 behind one address.
 * ponytail: in-memory fixed window, single instance only, and it sees the proxy's address behind a
 * proxy unless server.forward-headers-strategy is set. Use a shared store when running several instances.
 */
class AuthRateLimitFilter extends OncePerRequestFilter {
    static final int MAX_PER_WINDOW = 120;
    static final long WINDOW_MILLIS = 60_000;

    private final LongSupplier millis;
    private final Map<String, Integer> counts = new HashMap<>(); // cleared each window, so it cannot grow without end
    private long windowStart;

    AuthRateLimitFilter(LongSupplier millis) {
        this.millis = millis;
        windowStart = millis.getAsLong();
    }

    private synchronized boolean allow(String ip) {
        long now = millis.getAsLong();
        if (now - windowStart >= WINDOW_MILLIS) {
            counts.clear();
            windowStart = now;
        }
        return counts.merge(ip, 1, Integer::sum) <= MAX_PER_WINDOW;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (allow(req.getRemoteAddr())) {
            chain.doFilter(req, res);
        } else {
            SecurityConfig.writeError(res, 429, "Too many requests. Please wait a minute and try again.");
        }
    }
}
