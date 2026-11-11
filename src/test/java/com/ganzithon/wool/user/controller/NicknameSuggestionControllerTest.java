package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NicknameSuggestionControllerTest extends IntegrationTestSupport {

    private static final String URL = "/api/users/nickname-suggestion";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 인증_헤더_없이_호출하면_200과_닉네임_하나를_래퍼로_내려준다() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("USER_200_3"))
                .andExpect(jsonPath("$.message").value("닉네임을 추천했습니다."))
                .andExpect(jsonPath("$.data.nickname", matchesPattern("[가-힣]+ 양 [1-9]\\d{2}")))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 추천받은_닉네임은_2자_이상_10자_이하이다() throws Exception {
        for (int i = 0; i < 30; i++) {
            String body = mockMvc.perform(get(URL)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String nickname = body.replaceAll(".*\"nickname\":\"([^\"]+)\".*", "$1");

            assertThat(nickname.length()).as(nickname).isBetween(2, 10);
        }
    }

    @Test
    void 추천은_저장하지_않는다() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(get(URL)).andExpect(status().isOk());
        mockMvc.perform(get(URL)).andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    void 가입_API_경로와_충돌하지_않는다() throws Exception {
        mockMvc.perform(post("/api/users").contentType("application/json")
                        .content("{\"role\":\"YOUTH\",\"ageGroup\":\"20S\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get(URL)).andExpect(status().isOk());
    }
}
