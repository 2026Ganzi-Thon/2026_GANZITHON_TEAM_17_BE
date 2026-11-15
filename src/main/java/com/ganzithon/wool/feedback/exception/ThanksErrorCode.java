package com.ganzithon.wool.feedback.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ThanksErrorCode implements ErrorCode {
    ALREADY_SENT(HttpStatus.CONFLICT, "THANKS_409_001", "이미 고마움을 전한 답장이에요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
