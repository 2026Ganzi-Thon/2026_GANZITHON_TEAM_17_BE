package com.ganzithon.wool.certificate.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CertificateResponseCode implements ResponseCode {

    LIST(HttpStatus.OK, "CERT_200_1", "감사장 목록 조회에 성공했습니다."),
    DETAIL(HttpStatus.OK, "CERT_200_2", "감사장 조회에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
