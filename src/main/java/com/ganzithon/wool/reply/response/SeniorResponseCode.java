package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SeniorResponseCode implements ResponseCode {

    WORRY_LIST(HttpStatus.OK, "SENIOR_200_2", "고민 목록 조회에 성공했습니다."),
    WORRY_DETAIL(HttpStatus.OK, "SENIOR_200_4", "고민 상세 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
