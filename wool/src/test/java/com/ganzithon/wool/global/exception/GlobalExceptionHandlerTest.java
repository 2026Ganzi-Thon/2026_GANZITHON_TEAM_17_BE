package com.ganzithon.wool.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 비즈니스_예외는_에러코드와_data를_래퍼로_내려준다() throws Exception {
        mockMvc.perform(get("/business"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("USER_403_001"))
                .andExpect(jsonPath("$.message").value("이용할 수 없는 기능입니다."))
                .andExpect(jsonPath("$.data.restrictedUntil").value("2026-11-10"));
    }

    @Test
    void 검증_실패는_COMMON_400이다() throws Exception {
        mockMvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"));
    }

    @Test
    void 본문_파싱_실패는_COMMON_400이다() throws Exception {
        mockMvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void 예상하지_못한_예외는_COMMON_500이다() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("COMMON_500"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    static class TestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(CommonErrorCode.FORBIDDEN_ROLE, Map.of("restrictedUntil", "2026-11-10"));
        }

        @PostMapping("/valid")
        void valid(@Valid @RequestBody Body body) {
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }
    }

    record Body(@NotBlank String name) {
    }
}
