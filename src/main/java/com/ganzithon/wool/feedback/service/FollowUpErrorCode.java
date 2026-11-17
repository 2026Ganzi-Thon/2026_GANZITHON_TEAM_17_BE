package com.ganzithon.wool.feedback.service;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum FollowUpErrorCode implements ErrorCode {
    PRIVATE_WORRY(HttpStatus.BAD_REQUEST, "FOLLOWUP_400_001", "비공개 고민의 후일담은 전체 공개로 쓸 수 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "FOLLOWUP_404_001", "후일담을 찾을 수 없습니다."),
    VISIBILITY_CONFLICT(HttpStatus.CONFLICT, "FOLLOWUP_409_002", "비공개 후일담은 전체 공개로 바꿀 수 없어요."),
    ALREADY_SENT(HttpStatus.CONFLICT, "FOLLOWUP_409_001", "이미 후일담을 전했어요.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
