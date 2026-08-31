package com.saicomex.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT mint/parse.
 *
 * <p>Unlike the fleet API — where the role→permission mapping is a Java
 * {@code switch} — permissions here are read from {@code role_permissions} at
 * login and embedded as a claim. SRS §37 requires the permission model to be
 * configurable, and a switch statement is the one thing an administrator
 * cannot configure.
 *
 * <p>Consequence to be aware of: a permission change takes effect on the
 * user's <em>next login</em>, not immediately. {@link PermissionService}
 * therefore re-checks against the database for every write operation; the
 * token claim only drives nav visibility in the UI.
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set — app.jwt.secret is empty");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters (got " + secret.length() + ")");
        }
        if (secret.toLowerCase().contains("change") || secret.toLowerCase().contains("local-dev")) {
            // Local placeholders are fine locally; prod refuses to boot on one.
            String profiles = System.getProperty("spring.profiles.active", System.getenv("SPRING_PROFILES_ACTIVE"));
            if (profiles != null && profiles.contains("prod")) {
                throw new IllegalStateException("JWT_SECRET is still a placeholder value — set a real secret before running prod");
            }
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String roleCode, List<String> permissions, Long userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim("uid", userId)
                .claim("role", roleCode)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key())
                .compact();
    }

    public String extractEmail(String token)  { return parse(token).getSubject(); }
    public String extractRole(String token)   { return parse(token).get("role", String.class); }
    public String extractJti(String token)    { return parse(token).getId(); }
    public Long   extractUserId(String token) {
        Number n = parse(token).get("uid", Number.class);
        return n == null ? null : n.longValue();
    }
    public Date extractExpiry(String token)   { return parse(token).getExpiration(); }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Object perms = parse(token).get("permissions");
        return (perms instanceof List<?> list) ? (List<String>) list : List.of();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * True only when the token is well-formed and correctly signed but past
     * expiry. Lets the filter tell the client "sign in again" instead of the
     * ambiguous "you don't have permission".
     */
    public boolean isExpired(String token) {
        try {
            parse(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
