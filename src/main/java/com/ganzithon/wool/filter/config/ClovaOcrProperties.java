package com.ganzithon.wool.filter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// 손편지 사진의 글자를 읽는 네이버 클라우드 CLOVA OCR(General) 설정.
// url은 콘솔의 도메인에서 받은 호출 주소(Invoke URL) 전체이고, secret은 같은 도메인의 시크릿 키다.
// 둘 중 하나라도 비어 있으면 OCR을 부르지 않고 손편지 내용 검사를 건너뛴다.
@ConfigurationProperties(prefix = "clova.ocr")
public record ClovaOcrProperties(
        @DefaultValue("") String url,
        @DefaultValue("") String secret,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("2") int maxConcurrentCalls) {

    public boolean hasCredentials() {
        return url != null && !url.isBlank() && secret != null && !secret.isBlank();
    }
}
