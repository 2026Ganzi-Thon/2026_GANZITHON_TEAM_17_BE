package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SttService {
    private final AudioValidator validator;
    private final GroqSttClient client;

    public SttController.SttResponse transcribe(MultipartFile audio) {
        validator.validate(audio);
        String text = client.transcribe(audio);
        if (text == null || text.isBlank()) throw new BusinessException(SttErrorCode.TRANSCRIPTION_FAILED);
        return new SttController.SttResponse(text);
    }
}
