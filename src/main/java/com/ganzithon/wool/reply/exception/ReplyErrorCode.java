package com.ganzithon.wool.reply.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReplyErrorCode implements ErrorCode {

    RESTRICTED(HttpStatus.FORBIDDEN, "REPLY_403_002", "지금은 답장할 수 없어요."),
    ALREADY_REPLIED(HttpStatus.CONFLICT, "REPLY_409_001", "이미 답장한 고민이에요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
