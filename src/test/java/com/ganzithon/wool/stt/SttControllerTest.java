package com.ganzithon.wool.stt;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SttControllerTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean GroqSttClient client;
    private User senior;
    private User youth;

    @BeforeEach
    void setup() {
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "음성선배"));
        youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "음성청년"));
    }

    @Test
    void 인증과_역할을_검사한다() throws Exception {
        mvc.perform(multipart("/api/stt")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/stt").header("X-User-Id", youth.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }

    @Test
    void 파일_누락과_잘못된_파일을_거부한다() throws Exception {
        mvc.perform(multipart("/api/stt").header("X-User-Id", senior.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STT_400_001"));
        var fake = new MockMultipartFile("audio", "audio.wav", "audio/wav", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/stt").file(fake).header("X-User-Id", senior.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STT_400_001"));
    }
}
