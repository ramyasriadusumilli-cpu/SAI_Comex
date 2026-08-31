package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.entity.RevokedToken;
import com.saicomex.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Sign-out for stateless JWTs: the token's jti is recorded until its natural
 * expiry, and {@link com.saicomex.security.JwtAuthFilter} rejects it in the
 * meantime. Expired rows are purged nightly so the table tracks active
 * sessions, not history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return jti != null && revokedTokenRepository.existsByJti(jti);
    }

    @Transactional
    public void revoke(String jti, Date expiry) {
        if (jti == null || revokedTokenRepository.existsByJti(jti)) return;
        RevokedToken token = new RevokedToken();
        token.setJti(jti);
        token.setExpiresAt(expiry == null
                ? LocalDateTime.now().plusDays(1)
                : LocalDateTime.ofInstant(expiry.toInstant(), java.time.ZoneId.systemDefault()));
        token.setRevokedAt(LocalDateTime.now());
        token.setRevokedBy(AuditContext.currentUser());
        revokedTokenRepository.save(token);
    }

    /** 03:20 daily — a revoked token past its own expiry is already useless. */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeExpired() {
        revokedTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        log.debug("Purged expired revoked-token rows");
    }
}
