package com.ganzithon.wool.global.config;

import com.ganzithon.wool.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CORS_ALLOWED_ORIGINS로 출처를 바꾸면 기본값은 더 이상 쓰이지 않고, 앞뒤 공백과 빈 항목은 무시한다.
@AutoConfigureMockMvc
@TestPropertySource(properties = "cors.allowed-origins= https://front.example , ,https://other.example ")
class CorsConfigOverrideTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private CorsProperties corsProperties;

    @Test
    void 설정한_출처만_허용하고_기본값은_쓰지_않는다() throws Exception {
        assertThat(corsProperties.allowedOrigins()).isEqualTo(List.of("https://front.example", "https://other.example"));

        mockMvc.perform(get("/api/health").header("Origin", "https://front.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://front.example"));
        mockMvc.perform(get("/api/health").header("Origin", "https://hanol-ganzi-team17.web.app"))
                .andExpect(status().isForbidden());
    }
}
