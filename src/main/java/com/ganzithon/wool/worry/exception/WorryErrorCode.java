package com.ganzithon.wool.worry.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WorryErrorCode implements ErrorCode {
    FORBIDDEN_CHANGE(HttpStatus.FORBIDDEN, "WORRY_403_002", "내 고민만 변경할 수 있습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "WORRY_404_001", "고민을 찾을 수 없습니다."),
    VISIBILITY_CONFLICT(HttpStatus.CONFLICT, "WORRY_409_001", "비공개 글은 전체 공개로 바꿀 수 없어요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
