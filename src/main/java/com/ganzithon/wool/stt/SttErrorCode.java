package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SttErrorCode implements ErrorCode {
    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "STT_400_001", "녹음 파일을 확인해주세요."),
    TRANSCRIPTION_FAILED(HttpStatus.BAD_GATEWAY, "STT_502_001", "말씀을 잘 못 알아들었어요. 다시 말씀해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
