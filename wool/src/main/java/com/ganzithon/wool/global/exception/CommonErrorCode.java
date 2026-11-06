package com.ganzithon.wool.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "입력값이 올바르지 않습니다."),
    UNREGISTERED_USER(HttpStatus.UNAUTHORIZED, "USER_401_001", "등록되지 않은 사용자입니다."),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "USER_403_001", "이용할 수 없는 기능입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
