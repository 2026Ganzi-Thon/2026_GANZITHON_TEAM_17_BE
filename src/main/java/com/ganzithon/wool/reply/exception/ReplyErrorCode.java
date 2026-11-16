package com.ganzithon.wool.reply.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReplyErrorCode implements ErrorCode {

    FORBIDDEN_VIEW(HttpStatus.FORBIDDEN, "REPLY_403_001", "볼 수 없는 답장입니다."),
    RESTRICTED(HttpStatus.FORBIDDEN, "REPLY_403_002", "지금은 답장할 수 없어요."),
    FORBIDDEN_DELETE(HttpStatus.FORBIDDEN, "REPLY_403_003", "삭제할 권한이 없습니다."),
    FORBIDDEN_UPDATE(HttpStatus.FORBIDDEN, "REPLY_403_004", "수정할 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "REPLY_404_001", "답장을 찾을 수 없습니다."),
    ALREADY_REPLIED(HttpStatus.CONFLICT, "REPLY_409_001", "이미 답장한 고민이에요."),
    FEEDBACK_RECEIVED(HttpStatus.CONFLICT, "REPLY_409_002", "이미 고마움을 받은 답장은 수정할 수 없어요."),
    LETTER_NOT_EDITABLE(HttpStatus.CONFLICT, "REPLY_409_003", "손편지 답장은 수정할 수 없어요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
