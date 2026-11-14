package com.ganzithon.wool.user.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER_409_001", "이미 사용 중인 닉네임입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
