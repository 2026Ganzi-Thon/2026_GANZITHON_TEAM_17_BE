package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private MvcResult join(String body) throws Exception {
        // 실패했을 때 원인이 가입 응답 코드에서 바로 보이도록 먼저 201인지 확인한다.
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    void 가입하면_201과_발급된_사용자_정보를_래퍼로_내려준다() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SENIOR\",\"ageGroup\":\"70S_PLUS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("USER_201_1"))
                .andExpect(jsonPath("$.message").value("가입에 성공했습니다."))
                .andExpect(jsonPath("$.data.userId", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                .andExpect(jsonPath("$.data.role").value("SENIOR"))
                .andExpect(jsonPath("$.data.ageGroup").value("70S_PLUS"))
                .andExpect(jsonPath("$.data.nickname", matchesPattern("[가-힣]+ 양 [1-9]\\d{2}")))
                // 명세의 네 필드 외에 다른 값이 내려가지 않는다.
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void 발급된_사용자_ID로_저장된_사용자를_다시_찾을_수_있다() throws Exception {
        String body = join("{\"role\":\"YOUTH\",\"ageGroup\":\"20S\"}").getResponse().getContentAsString();
        String userId = body.replaceAll(".*\"userId\":\"([^\"]+)\".*", "$1");

        User saved = userRepository.findById(userId).orElseThrow();

        assertThat(saved.isYouth()).isTrue();
        assertThat(saved.getAgeGroup().getValue()).isEqualTo("20S");
        assertThat(UUID.fromString(saved.getId())).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"20S", "30S", "40S", "50S", "60S", "70S_PLUS"})
    void 청년과_어르신_모두_모든_나이대로_가입할_수_있다(String ageGroup) throws Exception {
        for (String role : new String[]{"YOUTH", "SENIOR"}) {
            mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"" + role + "\",\"ageGroup\":\"" + ageGroup + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.role").value(role))
                    .andExpect(jsonPath("$.data.ageGroup").value(ageGroup));
        }
    }

    @Test
    void 인증_헤더_없이도_가입할_수_있다() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"YOUTH\",\"ageGroup\":\"30S\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 여러_번_가입해도_닉네임이_겹치지_않는다() throws Exception {
        Set<String> nicknames = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String body = join("{\"role\":\"YOUTH\",\"ageGroup\":\"20S\"}").getResponse().getContentAsString();
            nicknames.add(body.replaceAll(".*\"nickname\":\"([^\"]+)\".*", "$1"));
        }

        assertThat(nicknames).hasSize(40);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"ageGroup\":\"20S\"}",
            "{\"role\":\"YOUTH\"}",
            "{\"role\":null,\"ageGroup\":\"20S\"}",
            "{\"role\":\"ADMIN\",\"ageGroup\":\"20S\"}",
            "{\"role\":\"YOUTH\",\"ageGroup\":\"10S\"}",
            "{\"role\":\"YOUTH\",\"ageGroup\":\"20\"}",
            "{\"role\":\"YOUTH\",\"ageGroup\":\"AGE_20S\"}",
            "{\"role\":\"YOUTH\",\"ageGroup\":20}",
            "이건 JSON이 아니에요"})
    void 필수값이_없거나_잘못된_값이면_400이다(String body) throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 요청에_닉네임을_보내도_무시하고_서버가_정한_닉네임을_쓴다() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"YOUTH\",\"ageGroup\":\"20S\",\"nickname\":\"내멋대로\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname", matchesPattern("[가-힣]+ 양 [1-9]\\d{2}")));
    }
}
