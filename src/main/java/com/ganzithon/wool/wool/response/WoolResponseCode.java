package com.ganzithon.wool.wool.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WoolResponseCode implements ResponseCode {

    RECORD(HttpStatus.OK, "WOOL_200_1", "털 기록 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
