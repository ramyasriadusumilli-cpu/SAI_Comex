package com.saicomex.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    /**
     * Every cache the application uses must be named here.
     *
     * <p>{@code ConcurrentMapCacheManager} constructed with an explicit name
     * list is fixed-size: asking it for an unlisted cache returns null, and
     * {@code @Cacheable} on that method then throws — which surfaces as a 403
     * on every authenticated request if {@code userActive} is the one missing.
     * Adding a {@code @Cacheable} method anywhere means adding its name here.
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "userActive",        // JwtAuthFilter — per-request account check
                "rolePermissions",   // permission codes for a role
                "systemConfig",      // configuration engine values
                "exchangeRates",     // latest rate per currency pair per day
                "referenceData"      // currencies, units, categories, contract types
        );
    }
}
