package com.ganzithon.wool.global.exception;

import lombok.Getter;

// 도메인 규칙 위반은 이 예외에 ErrorCode를 담아 던진다.
// data는 응답의 data로 내려간다 (예: REPLY_403_002의 restrictedUntil).
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }
}
