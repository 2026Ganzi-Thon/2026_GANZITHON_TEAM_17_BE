package com.ganzithon.wool.upload.exception;

import com.ganzithon.wool.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UploadErrorCode implements ErrorCode {

    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "UPLOAD_400_001", "사진을 확인해주세요."),
    TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_413_001", "사진 용량이 너무 커요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
