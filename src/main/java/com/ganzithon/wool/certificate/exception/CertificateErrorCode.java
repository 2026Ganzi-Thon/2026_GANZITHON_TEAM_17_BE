package com.ganzithon.wool.certificate.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CertificateErrorCode implements ErrorCode {

    FORBIDDEN(HttpStatus.FORBIDDEN, "CERT_403_001", "볼 수 없는 감사장입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "CERT_404_001", "감사장을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
