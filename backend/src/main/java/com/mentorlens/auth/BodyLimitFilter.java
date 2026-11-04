package com.mentorlens.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects bodies over the limit: by Content-Length, or by reading them when no length is declared (chunked). */
class BodyLimitFilter extends OncePerRequestFilter {
    static final int MAX_BYTES = 256 * 1024;
    private static final String TOO_LARGE = "Request body is too large";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() > MAX_BYTES) {
            SecurityConfig.writeError(res, 413, TOO_LARGE);
        } else if (req.getContentLengthLong() >= 0) {
            chain.doFilter(req, res); // the server enforces a declared length
        } else {
            byte[] body = req.getInputStream().readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                SecurityConfig.writeError(res, 413, TOO_LARGE);
            } else {
                chain.doFilter(new BufferedBody(req, body), res);
            }
        }
    }

    private static final class BufferedBody extends HttpServletRequestWrapper {
        private final byte[] body;

        BufferedBody(HttpServletRequest req, byte[] body) {
            super(req);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            String enc = getCharacterEncoding();
            Charset cs = enc == null ? StandardCharsets.UTF_8 : Charset.forName(enc);
            return new BufferedReader(new InputStreamReader(getInputStream(), cs));
        }
    }
}
