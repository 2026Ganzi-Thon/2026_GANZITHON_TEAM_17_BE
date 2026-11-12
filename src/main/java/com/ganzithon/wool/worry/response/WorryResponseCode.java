package com.ganzithon.wool.worry.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WorryResponseCode implements ResponseCode {
    CREATED(HttpStatus.CREATED, "WORRY_201_1", "고민이 선배님들께 전달됐어요."),
    MY_LIST(HttpStatus.OK, "WORRY_200_1", "내 고민 목록 조회에 성공했습니다."),
    VISIBILITY_CHANGED(HttpStatus.OK, "WORRY_200_3", "공개 범위를 변경했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
