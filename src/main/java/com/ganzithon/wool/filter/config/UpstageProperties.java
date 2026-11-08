package com.ganzithon.wool.filter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "upstage")
public record UpstageProperties(String apiKey, String baseUrl, String model, Duration timeout) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
