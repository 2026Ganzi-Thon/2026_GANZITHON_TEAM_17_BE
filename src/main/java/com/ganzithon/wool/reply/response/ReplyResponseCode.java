package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReplyResponseCode implements ResponseCode {

    VIEWED(HttpStatus.OK, "REPLY_200_1", "답장 조회에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "REPLY_201_1", "선배님의 털 한 올이 전해졌어요."),
    LIKE_TOGGLED(HttpStatus.OK, "LIKE_200_2", "좋아요 상태를 변경했습니다."),
    UPDATED(HttpStatus.OK, "REPLY_200_8", "답장을 수정했습니다."),
    DELETED(HttpStatus.OK, "REPLY_200_7", "답장을 삭제했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
