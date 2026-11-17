package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SeniorResponseCode implements ResponseCode {

    HOME(HttpStatus.OK, "SENIOR_200_1", "홈 정보 조회에 성공했습니다."),
    WORRY_LIST(HttpStatus.OK, "SENIOR_200_2", "고민 목록 조회에 성공했습니다."),
    REPLY_LIST(HttpStatus.OK, "SENIOR_200_3", "내가 보낸 답장 목록 조회에 성공했습니다."),
    WORRY_DETAIL(HttpStatus.OK, "SENIOR_200_4", "고민 상세 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
