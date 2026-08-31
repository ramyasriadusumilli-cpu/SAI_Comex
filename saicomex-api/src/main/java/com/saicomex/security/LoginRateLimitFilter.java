package com.saicomex.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force protection on {@code /api/auth/login} — 10 attempts per IP per
 * 15 minutes, then a 15-minute block. In-memory, which is correct for a
 * single-instance deployment; move to Redis if the API is ever scaled out.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Bucket(AtomicInteger count, Instant windowStart) {}

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(req.getMethod()) || !req.getRequestURI().endsWith("/api/auth/login")) {
            chain.doFilter(req, res);
            return;
        }

        String ip = clientIp(req);
        Instant now = Instant.now();
        Bucket bucket = buckets.compute(ip, (k, existing) -> {
            if (existing == null || Duration.between(existing.windowStart(), now).compareTo(WINDOW) > 0) {
                return new Bucket(new AtomicInteger(0), now);
            }
            return existing;
        });

        if (bucket.count().incrementAndGet() > MAX_ATTEMPTS) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            res.getWriter().write("{\"error\":\"Too many sign-in attempts. Try again in 15 minutes.\"}");
            return;
        }

        chain.doFilter(req, res);

        // A successful sign-in clears the counter so one fat-fingered password
        // does not count against the operator for the rest of the window.
        if (res.getStatus() == HttpStatus.OK.value()) {
            buckets.remove(ip);
        }
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
