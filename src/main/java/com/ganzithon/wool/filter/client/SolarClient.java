package com.ganzithon.wool.filter.client;

// 위기 표현을 Upstage Solar에게 물어보는 클라이언트.
public interface SolarClient {

    // API 키가 설정돼 있어 호출할 수 있으면 true. false면 호출하지 않고 키워드 목록으로 넘어간다.
    boolean isConfigured();

    // 위기 표현이 있으면 true. 호출 실패, 시간 초과, 알 수 없는 응답은 예외로 알린다.
    boolean isCrisis(String text);
}
