package com.ganzithon.wool.filter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({UpstageProperties.class, ClovaOcrProperties.class})
public class FilterConfig {
}
