package com.ganzithon.wool.global.config;

import com.ganzithon.wool.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest extends IntegrationTestSupport {

    record Sample(LocalDateTime createdAt) {
    }

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void 초가_정각인_시각은_초까지_쓰고_서울_오프셋이_붙는다() {
        String json = jsonMapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 11, 4, 20, 10, 0)));

        assertThat(json).isEqualTo("{\"createdAt\":\"2026-11-04T20:10:00+09:00\"}");
    }

    @Test
    void 마이크로초가_있으면_소수부도_함께_나간다() {
        String json = jsonMapper.writeValueAsString(
                new Sample(LocalDateTime.of(2026, 11, 4, 20, 10, 0, 123_456_000)));

        assertThat(json).isEqualTo("{\"createdAt\":\"2026-11-04T20:10:00.123456+09:00\"}");
    }

    @Test
    void 자정_직전과_연말도_날짜가_밀리지_않는다() {
        String json = jsonMapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 12, 31, 23, 59, 59)));

        assertThat(json).isEqualTo("{\"createdAt\":\"2026-12-31T23:59:59+09:00\"}");
    }
}
