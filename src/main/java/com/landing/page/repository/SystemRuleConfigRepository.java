package com.landing.page.repository;

import com.landing.page.entity.SystemRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemRuleConfigRepository extends JpaRepository<SystemRuleConfig, Long> {
    Optional<SystemRuleConfig> findByConfigKey(String configKey);
}
