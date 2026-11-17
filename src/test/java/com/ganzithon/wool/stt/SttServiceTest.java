package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SttServiceTest {
    @Test
    void 텍스트를_그대로_반환하고_빈_결과는_실패한다() {
        var validator = mock(AudioValidator.class);
        var client = mock(GroqSttClient.class);
        var audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1});
        var service = new SttService(validator, client);
        when(client.transcribe(audio)).thenReturn("  안녕하세요.\n", "  ");
        assertThat(service.transcribe(audio).text()).isEqualTo("  안녕하세요.\n");
        assertThatThrownBy(() -> service.transcribe(audio)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SttErrorCode.TRANSCRIPTION_FAILED);
    }
}
