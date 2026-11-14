package com.ganzithon.wool.report.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportResponseCode implements ResponseCode {

    REPLY_REPORTED(HttpStatus.CREATED, "REPORT_201_1", "신고가 접수되었고 답장을 숨겼습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
