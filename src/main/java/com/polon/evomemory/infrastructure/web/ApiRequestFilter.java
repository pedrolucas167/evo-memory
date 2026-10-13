package com.polon.evomemory.infrastructure.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRequestFilter extends OncePerRequestFilter {
    private final Semaphore permits;
    private final int maxBodyBytes;

    public ApiRequestFilter(@Value("${memory.api.max-concurrent:4}") int maxConcurrent,
                            @Value("${memory.api.max-body-bytes:65536}") int maxBodyBytes) {
        if (maxConcurrent < 1 || maxBodyBytes < 1 || maxBodyBytes > 1048576) {
            throw new IllegalArgumentException("Invalid API resource limits");
        }
        this.permits = new Semaphore(maxConcurrent);
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/")
                && !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            response.setStatus(429);
            response.setHeader("Retry-After", "1");
            return;
        }
        try {
            if (request.getContentLengthLong() > maxBodyBytes) {
                response.setStatus(413);
                return;
            }
            byte[] bytes = request.getInputStream().readNBytes(maxBodyBytes + 1);
            if (bytes.length > maxBodyBytes) {
                response.setStatus(413);
                return;
            }
            chain.doFilter(new BufferedRequest(request, bytes), response);
        } finally {
            permits.release();
        }
    }

    private static class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        BufferedRequest(HttpServletRequest request, byte[] bytes) { super(request); this.bytes = bytes; }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous request reads are not supported");
                }
            };
        }
    }
}
