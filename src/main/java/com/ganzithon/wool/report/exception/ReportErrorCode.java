package com.ganzithon.wool.report.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    ALREADY_REPORTED(HttpStatus.CONFLICT, "REPORT_409_001", "이미 신고한 답장입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
