package com.ganzithon.wool.feedback.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ThanksResponseCode implements ResponseCode {
    CREATED(HttpStatus.CREATED, "THANKS_201_1", "선배님께 마음이 전해졌어요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
