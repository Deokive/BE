package com.depth.deokive.system.config.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditorConfig {
    // AuditorAwareImpl을 찾아서 사용하는 Config
}
