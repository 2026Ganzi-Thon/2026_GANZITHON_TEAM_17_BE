package com.ganzithon.wool.global.response;

import org.springframework.http.HttpStatus;

// 각 도메인에서 enum으로 구현 (예: USER_201_1)
public interface ResponseCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
