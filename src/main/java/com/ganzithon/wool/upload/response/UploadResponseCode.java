package com.ganzithon.wool.upload.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UploadResponseCode implements ResponseCode {

    LETTER_UPLOADED(HttpStatus.CREATED, "UPLOAD_201_1", "손편지 사진을 올렸습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
