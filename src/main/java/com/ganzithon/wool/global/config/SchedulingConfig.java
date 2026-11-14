package com.ganzithon.wool.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// wool.scheduling.enabled=false면 스케줄링 자체를 켜지 않는다. 테스트는 DB를 공유하므로 끈다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "wool.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
