package com.saicomex.security;

import com.saicomex.service.TokenBlacklistService;
import com.saicomex.service.UserCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserCacheService userCache;
    private final TokenBlacklistService blacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.isValid(token)) {
                if (blacklist.isRevoked(jwtUtil.extractJti(token))) {
                    res.setHeader("X-Auth-Status", "token-invalid");
                } else {
                    String email = jwtUtil.extractEmail(token);
                    String role  = jwtUtil.extractRole(token);

                    if (userCache.isActiveUser(email)) {
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        // Permissions land as bare authorities so @PreAuthorize can
                        // say hasAuthority('shafts.edit') without a custom resolver.
                        for (String p : jwtUtil.extractPermissions(token)) {
                            authorities.add(new SimpleGrantedAuthority(p));
                        }
                        var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        // Account disabled or deleted — force re-authentication rather
                        // than showing a vague permission error.
                        res.setHeader("X-Auth-Status", "token-invalid");
                    }
                }
            } else if (jwtUtil.isExpired(token)) {
                res.setHeader("X-Auth-Status", "token-expired");
            } else {
                res.setHeader("X-Auth-Status", "token-invalid");
            }
        }
        chain.doFilter(req, res);
    }
}
