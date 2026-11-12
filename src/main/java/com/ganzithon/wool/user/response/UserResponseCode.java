package com.ganzithon.wool.user.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserResponseCode implements ResponseCode {

    JOINED(HttpStatus.CREATED, "USER_201_1", "가입에 성공했습니다."),
    NICKNAME_UPDATED(HttpStatus.OK, "USER_200_1", "닉네임을 바꿨습니다."),
    NICKNAME_SUGGESTED(HttpStatus.OK, "USER_200_3", "닉네임을 추천했습니다."),
    MY_INFO(HttpStatus.OK, "USER_200_2", "내 정보 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
