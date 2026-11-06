package com.ganzithon.wool.global.exception;

import org.springframework.http.HttpStatus;

// 각 도메인에서 enum으로 구현 (예: REPLY_409_001)
public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
