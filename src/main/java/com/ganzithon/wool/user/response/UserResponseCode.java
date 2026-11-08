package com.ganzithon.wool.user.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserResponseCode implements ResponseCode {

    JOINED(HttpStatus.CREATED, "USER_201_1", "가입에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
