package com.ganzithon.wool.global.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    @Test
    void 설정이_없으면_어떤_출처도_허용하지_않는다() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(List.of()).allowedOrigins()).isEmpty();
        assertThat(new CorsProperties(Arrays.asList("", "  ")).allowedOrigins()).isEmpty();
    }

    @Test
    void 앞뒤_공백을_지우고_빈_항목은_뺀다() {
        assertThat(new CorsProperties(Arrays.asList(" https://a.example ", "", "https://b.example")).allowedOrigins())
                .containsExactly("https://a.example", "https://b.example");
    }
}
