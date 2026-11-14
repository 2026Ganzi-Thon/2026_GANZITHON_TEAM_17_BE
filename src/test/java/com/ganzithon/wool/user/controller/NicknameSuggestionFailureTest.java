package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.global.exception.GlobalExceptionHandler;
import com.ganzithon.wool.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 닉네임 조합이 모두 소진돼 생성기가 실패하는 경우를 가짜 서비스로 재현한다. (실제로 18000개를 채울 수 없으므로)
class NicknameSuggestionFailureTest {

    private final UserService userService = mock(UserService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NicknameController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 닉네임을_만들지_못하면_COMMON_500으로_알린다() throws Exception {
        when(userService.suggestNickname()).thenThrow(new IllegalStateException("사용할 수 있는 닉네임을 만들지 못했습니다."));

        mockMvc.perform(get("/api/nicknames/suggestion"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("COMMON_500"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
