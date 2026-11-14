package com.ganzithon.wool.notification.entity;

// 알림을 눌렀을 때 이동할 화면. DB의 notifications.target_kind ENUM과 같은 값이다.
public enum TargetKind {
    REPLY(true),        // 답장 상세 (target id = 답장 ID)
    RECEIVED(false),    // 받은 마음
    MY_REPLIES(false),  // 내가 보낸 답장
    SANCTION(false),    // 제재 안내
    CERTIFICATE(true);  // 감사장 (target id = 감사장 ID)

    private final boolean requiresTargetId;

    TargetKind(boolean requiresTargetId) {
        this.requiresTargetId = requiresTargetId;
    }

    // 스키마 주석대로 REPLY, CERTIFICATE만 대상 ID를 가진다.
    public boolean requiresTargetId() {
        return requiresTargetId;
    }
}
