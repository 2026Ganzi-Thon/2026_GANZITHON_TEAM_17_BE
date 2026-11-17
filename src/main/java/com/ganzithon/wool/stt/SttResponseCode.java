package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SttResponseCode implements ResponseCode {
    TRANSCRIBED(HttpStatus.OK, "STT_200_1", "음성을 글로 바꾸었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
