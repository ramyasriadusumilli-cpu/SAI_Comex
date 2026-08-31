package com.saicomex.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The acting user for the current request, for audit stamping.
 *
 * <p>Reads the Spring Security context rather than taking the email as a
 * method parameter, so no service can accidentally stamp a record with a user
 * other than the authenticated caller. Background jobs run outside a security
 * context and stamp {@code system}.
 */
public final class AuditContext {

    public static final String SYSTEM = "system";

    private AuditContext() {}

    /** Email of the authenticated caller, or {@code system} for background work. */
    public static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return SYSTEM;
        }
        String name = auth.getName();
        return (name == null || name.isBlank() || "anonymousUser".equals(name)) ? SYSTEM : name;
    }

    /** Role code of the authenticated caller, or {@code null}. */
    public static String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return null;
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse(null);
    }
}
