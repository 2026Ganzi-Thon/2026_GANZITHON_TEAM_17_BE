package com.ganzithon.wool.report.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    SELF_REPORT(HttpStatus.FORBIDDEN, "REPORT_403_001", "내 고민은 신고할 수 없어요."),
    WORRY_ALREADY_REPORTED(HttpStatus.CONFLICT, "REPORT_409_002", "이미 신고한 고민입니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "REPORT_409_001", "이미 신고한 답장입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
