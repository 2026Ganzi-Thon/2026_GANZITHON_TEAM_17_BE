package com.ganzithon.wool.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonResponseCode implements ResponseCode {

    OK(HttpStatus.OK, "COMMON_200_1", "정상입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
