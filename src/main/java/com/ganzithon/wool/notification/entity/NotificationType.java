package com.ganzithon.wool.notification.entity;

// 알림 종류. DB의 notifications.type ENUM과 같은 값이다.
public enum NotificationType {
    REPLY_ARRIVED,        // 청년: 답장 도착
    FOLLOW_UP_REMINDER,   // 청년: 답장 7일 후 후일담 미작성 시 1회
    THANKS_ARRIVED,       // 어르신: 고마움 도착
    FOLLOW_UP_ARRIVED,    // 어르신: 후일담 도착
    SANCTION,             // 어르신: 경고·답장 제한·영구 제한 적용
    CERTIFICATE_ISSUED    // 어르신: 감사장 발급 (누적 20통마다)
}
