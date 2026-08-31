package com.saicomex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SAIComex Mining Operations & Management Platform — API.
 *
 * <p>Runs the same stack as the SAI Fleet system it sits beside: Spring Boot
 * 3.3 on Java 21, PostgreSQL 16 behind Flyway, MinIO for documents, stateless
 * JWT auth. Deployed to the same host under its own containers and subdomain.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class SaicomexApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaicomexApplication.class, args);
    }
}
