package com.ganzithon.wool.global.config;

import com.ganzithon.wool.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 프론트 출처(기본값: Firebase 배포 주소와 로컬 개발 주소)만 /api/**를 호출할 수 있고, 그 밖의 출처는 허용 헤더를 받지 못한다.
@AutoConfigureMockMvc
class CorsConfigTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "https://hanol-ganzi-team17.web.app",
            "https://hanol-ganzi-team17.firebaseapp.com",
            "http://localhost:5173",
            "http://localhost:3000"})
    void 허용한_출처는_요청에_허용_헤더를_받는다(String origin) throws Exception {
        mockMvc.perform(get("/api/health").header("Origin", origin))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void 사전_요청은_허용한_출처의_메서드와_헤더를_허용하고_결과를_캐시한다() throws Exception {
        mockMvc.perform(options("/api/replies/1")
                        .header("Origin", "https://hanol-ganzi-team17.web.app")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "content-type,x-user-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://hanol-ganzi-team17.web.app"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Allow-Headers"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example",
            "http://hanol-ganzi-team17.web.app",
            "https://hanol-ganzi-team17.web.app.evil.example",
            "https://other-project.web.app",
            "http://localhost:8081",
            "null"})
    void 허용하지_않은_출처는_거부되고_허용_헤더를_받지_못한다(String origin) throws Exception {
        mockMvc.perform(get("/api/health").header("Origin", origin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void 허용하지_않은_출처의_사전_요청은_거부된다() throws Exception {
        mockMvc.perform(options("/api/replies/1")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void 허용하지_않은_요청_헤더는_사전_요청에서_거부된다() throws Exception {
        mockMvc.perform(options("/api/replies/1")
                        .header("Origin", "https://hanol-ganzi-team17.web.app")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "x-admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 출처가_없는_요청은_그대로_처리한다() throws Exception {
        // 서버끼리 부르거나 curl로 부르는 요청에는 Origin 헤더가 없다. CORS는 브라우저만의 규칙이다.
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
