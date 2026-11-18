package com.ganzithon.wool.global.config;

import com.ganzithon.wool.global.auth.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final CorsProperties corsProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    // 설정(cors.allowed-origins)에 적은 프론트 출처만 허용한다. 인증이 쿠키가 아니라 X-User-Id 헤더라서 자격 증명(쿠키)은 허용하지 않는다.
    // 사전 요청(OPTIONS) 결과는 1시간 캐시해서, 프론트가 요청마다 사전 요청을 다시 보내 서버에 부담을 주지 않게 한다.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", USER_ID_HEADER)
                .maxAge(3600);
    }
}
