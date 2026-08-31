package com.saicomex.repository;

import com.saicomex.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    List<SystemConfig> findAllByCategoryOrderByConfigKeyAsc(String category);

    Optional<SystemConfig> findByConfigKey(String configKey);
}
