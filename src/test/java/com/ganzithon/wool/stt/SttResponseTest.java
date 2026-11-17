package com.ganzithon.wool.stt;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SttResponseTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean AudioValidator validator;
    @MockitoBean GroqSttClient client;

    @Test
    void 성공과_외부_서비스_오류를_명세대로_반환한다() throws Exception {
        var senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "음성선배"));
        var audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1});
        when(client.transcribe(any())).thenReturn("안녕하세요").thenThrow(new BusinessException(SttErrorCode.TRANSCRIPTION_FAILED));
        mvc.perform(multipart("/api/stt").file(audio).header("X-User-Id", senior.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("STT_200_1"))
                .andExpect(jsonPath("$.data.text").value("안녕하세요"));
        mvc.perform(multipart("/api/stt").file(audio).header("X-User-Id", senior.getId()))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("STT_502_001"));
    }
}
