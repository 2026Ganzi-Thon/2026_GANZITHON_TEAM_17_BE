package com.ganzithon.wool.filter.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FilterErrorCode implements ErrorCode {

    BANNED_WORD(HttpStatus.UNPROCESSABLE_ENTITY, "FILTER_422_001", "사용할 수 없는 표현이 포함되어 있어요."),
    PHONE_NUMBER(HttpStatus.UNPROCESSABLE_ENTITY, "FILTER_422_002", "전화번호는 적을 수 없어요."),
    EXTERNAL_CONTACT(HttpStatus.UNPROCESSABLE_ENTITY, "FILTER_422_003", "링크나 외부 연락처는 적을 수 없어요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
