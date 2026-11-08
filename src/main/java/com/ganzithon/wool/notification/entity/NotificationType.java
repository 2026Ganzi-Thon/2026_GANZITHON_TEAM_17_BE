package com.ganzithon.wool.notification.entity;

// 알림 종류. DB의 notifications.type ENUM과 같은 값이다.
// 종류마다 눌렀을 때 이동하는 화면(TargetKind)이 정해져 있다. (노션 NOTI-001의 알림 종류 표)
public enum NotificationType {
    REPLY_ARRIVED(TargetKind.REPLY),               // 청년: 답장 도착
    FOLLOW_UP_REMINDER(TargetKind.REPLY),          // 청년: 답장 7일 후 후일담 미작성 시 1회
    THANKS_ARRIVED(TargetKind.RECEIVED),           // 어르신: 고마움 도착
    FOLLOW_UP_ARRIVED(TargetKind.RECEIVED),        // 어르신: 후일담 도착
    SANCTION(TargetKind.SANCTION),                 // 어르신: 경고·답장 제한·영구 제한 적용
    CERTIFICATE_ISSUED(TargetKind.CERTIFICATE);    // 어르신: 감사장 발급 (누적 20통마다)

    private final TargetKind targetKind;

    NotificationType(TargetKind targetKind) {
        this.targetKind = targetKind;
    }

    public TargetKind targetKind() {
        return targetKind;
    }
}
