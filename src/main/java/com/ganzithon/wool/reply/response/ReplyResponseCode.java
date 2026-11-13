package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReplyResponseCode implements ResponseCode {

    CREATED(HttpStatus.CREATED, "REPLY_201_1", "선배님의 털 한 올이 전해졌어요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
