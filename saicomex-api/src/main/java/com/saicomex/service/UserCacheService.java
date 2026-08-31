package com.saicomex.service;

import com.saicomex.entity.User;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Per-request user lookups, cached.
 *
 * <p>{@link com.saicomex.security.JwtAuthFilter} calls {@link #isActiveUser}
 * on every authenticated request; without a cache that is one database
 * round-trip per API call. The cache name {@code userActive} must stay in
 * {@link com.saicomex.config.CacheConfig}'s list — an unnamed cache resolves
 * to null at runtime and every request then fails authorisation.
 *
 * <p>Deactivating or deleting a user must evict, or their token keeps working
 * until the entry expires. Every write path that changes a user's status goes
 * through {@link #evict}.
 */
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserRepository userRepository;

    @Cacheable(value = "userActive", key = "#email.toLowerCase()")
    @Transactional(readOnly = true)
    public boolean isActiveUser(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .filter(u -> u.getLockedUntil() == null || u.getLockedUntil().isBefore(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    @CacheEvict(value = "userActive", key = "#email.toLowerCase()")
    public void evict(String email) {
        // Annotation-driven; nothing to do in the body.
    }

    @CacheEvict(value = "userActive", allEntries = true)
    public void evictAll() {
        // Used when a role's permissions change and every session is affected.
    }
}
