package com.ganzithon.wool.worry.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.filter.client.SolarClient;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.repository.WorryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class WorryControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorryRepository worryRepository;
    @MockitoBean private SolarClient solarClient;
    private User youth;
    private User senior;

    @BeforeEach
    void setUp() {
        youth = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "고민청년"));
        senior = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "고민선배"));
    }

    private static String body(String title, String content) {
        return "{\"title\":\"" + title + "\",\"content\":\"" + content
                + "\",\"tag\":\"CAREER\",\"visibility\":\"PUBLIC\"}";
    }

    @Test
    void 청년의_원문을_저장하고_위기_감지_결과를_반환한다() throws Exception {
        when(solarClient.isConfigured()).thenReturn(true);
        when(solarClient.isCrisis("힘든 본문")).thenReturn(true);
        mockMvc.perform(post("/api/worries").header("X-User-Id", youth.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body("고민 제목", "힘든 본문")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WORRY_201_1"))
                .andExpect(jsonPath("$.message").value("고민이 선배님들께 전달됐어요."))
                .andExpect(jsonPath("$.data.worryId").isNumber())
                .andExpect(jsonPath("$.data.crisisDetected").value(true))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
        assertThat(worryRepository.findAll()).singleElement().satisfies(worry -> {
            assertThat(worry.getContent()).isEqualTo("힘든 본문");
            assertThat(worry.getAuthor().getId()).isEqualTo(youth.getId());
        });
        verify(solarClient).isCrisis("힘든 본문");
    }

    @Test
    void 인증과_역할을_검사한다() throws Exception {
        mockMvc.perform(post("/api/worries").contentType(MediaType.APPLICATION_JSON).content(body("제목", "본문")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        mockMvc.perform(post("/api/worries").header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body("제목", "본문")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
        assertThat(worryRepository.count()).isZero();
    }

    @Test
    void 잘못된_입력과_차단된_내용은_저장하지_않는다() throws Exception {
        for (String input : new String[]{"{}", body(" ", "본문"), body("가".repeat(41), "본문"),
                "{\"title\":\"제목\",\"content\":\"본문\",\"tag\":\"PARENTING\",\"visibility\":\"PUBLIC\"}",
                "{\"title\":\"제목\",\"content\":\"본문\",\"tag\":\"BAD\",\"visibility\":\"PUBLIC\"}",
                "{\"title\":\"제목\",\"content\":\"본문\",\"tag\":0,\"visibility\":\"PUBLIC\"}"}) {
            mockMvc.perform(post("/api/worries").header("X-User-Id", youth.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        }
        mockMvc.perform(post("/api/worries").header("X-User-Id", youth.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body("제목", "010-1234-5678")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILTER_422_002"));
        assertThat(worryRepository.count()).isZero();
        verifyNoInteractions(solarClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAREER", "LOVE", "FAMILY", "RELATIONSHIP", "LIVING", "MIND", "HEALTH", "OTHER"})
    void 정해진_여덟_태그로_고민을_작성할_수_있다(String tag) throws Exception {
        String input = body("고민 제목", "고민 내용").replace("CAREER", tag);
        mockMvc.perform(post("/api/worries").header("X-User-Id", youth.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isCreated());
        assertThat(worryRepository.findAll()).singleElement()
                .satisfies(worry -> assertThat(worry.getTag().name()).isEqualTo(tag));
    }

    @Test
    void 위기_감지_호출에_실패해도_키워드로_판단하고_저장한다() throws Exception {
        when(solarClient.isConfigured()).thenReturn(true);
        when(solarClient.isCrisis("죽고 싶어요")).thenThrow(new IllegalStateException());
        mockMvc.perform(post("/api/worries").header("X-User-Id", youth.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body("고민", "죽고 싶어요")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.crisisDetected").value(true));
        assertThat(worryRepository.count()).isEqualTo(1);
    }
}
