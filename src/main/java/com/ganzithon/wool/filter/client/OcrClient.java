package com.ganzithon.wool.filter.client;

// 사진에 적힌 글자를 읽어 주는 클라이언트. (손편지 내용 검사용)
public interface OcrClient {

    // 호출 주소와 키가 설정돼 있어 호출할 수 있으면 true. false면 호출하지 않고 검사를 건너뛴다.
    boolean isConfigured();

    // 사진(jpg 또는 png)에서 읽은 글자를 이어 붙여 돌려준다. 글자가 없으면 빈 문자열이다.
    // 호출 실패, 시간 초과, 동시 호출 한도 초과, 알 수 없는 응답은 예외로 알린다.
    String extractText(byte[] image, String format);
}
