package com.ganzithon.wool.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 운영 설정 파일에서 API 명세와 Swagger가 꺼져 있는지 확인한다. (실수로 지워지면 전체 API 구조가 외부에 공개된다)
class SwaggerProdConfigTest {

    @Test
    void 운영_설정_파일은_Swagger와_API_명세를_끈다() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));

        assertThat(sources.get(0).getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(sources.get(0).getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }
}
