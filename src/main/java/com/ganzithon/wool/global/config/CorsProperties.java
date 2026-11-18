package com.ganzithon.wool.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// 브라우저에서 /api/**를 부를 수 있는 프론트 출처(주소). 쉼표로 여러 개를 적는다. (환경변수 CORS_ALLOWED_ORIGINS)
// 출처는 "https://도메인"처럼 끝에 슬래시나 경로 없이, 프로토콜과 포트까지 정확히 적어야 한다.
// 목록에 없는 출처의 요청은 브라우저가 막는다. 비워 두면 어떤 출처도 허용하지 않는다.
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
