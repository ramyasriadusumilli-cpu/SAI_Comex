package com.saicomex.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coarse per-IP request ceiling (300/min), matching the fleet API's global
 * limiter. It is a blunt instrument against scripted scraping, not a fairness
 * mechanism — the offline mobile sync posts in batches and stays well under it.
 */
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_PER_MINUTE = 300;

    private record Window(AtomicInteger count, long minute) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (req.getRequestURI().startsWith("/actuator/health")) {
            chain.doFilter(req, res);
            return;
        }

        long minute = Instant.now().getEpochSecond() / 60;
        String ip = clientIp(req);

        Window w = windows.compute(ip, (k, existing) ->
                (existing == null || existing.minute() != minute)
                        ? new Window(new AtomicInteger(0), minute)
                        : existing);

        if (w.count().incrementAndGet() > MAX_PER_MINUTE) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }

        // Keep the map from growing without bound on a long-running instance.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }

        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}
