package com.ganzithon.wool.notification.entity;

import com.ganzithon.wool.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedEntity {

    public static final int TITLE_MAX_LENGTH = 255;
    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수신자 (users.id)
    @Column(name = "user_id", columnDefinition = "CHAR(36)", nullable = false, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false,
            columnDefinition = "ENUM('REPLY_ARRIVED', 'FOLLOW_UP_REMINDER', 'THANKS_ARRIVED', 'FOLLOW_UP_ARRIVED', 'SANCTION', 'CERTIFICATE_ISSUED')")
    private NotificationType type;

    @Column(name = "title", length = TITLE_MAX_LENGTH, nullable = false, updatable = false)
    private String title;

    @Column(name = "content", length = CONTENT_MAX_LENGTH, updatable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false, updatable = false,
            columnDefinition = "ENUM('REPLY', 'RECEIVED', 'MY_REPLIES', 'SANCTION', 'CERTIFICATE')")
    private TargetKind targetKind;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    private Notification(String userId, NotificationType type, String title, String content,
                         TargetKind targetKind, Long targetId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.targetKind = targetKind;
        this.targetId = targetId;
    }

    // 스키마가 허용하지 않는 값(빈 문구, 너무 긴 문구, 대상 ID 규칙 위반)은 저장 전에 개발 실수로 알린다.
    public static Notification of(String userId, NotificationType type, String title, String content,
                                   TargetKind targetKind, Long targetId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("수신자는 필수입니다.");
        }
        if (type == null || targetKind == null) {
            throw new IllegalArgumentException("알림 종류와 이동 대상 종류는 필수입니다.");
        }
        if (type.targetKind() != targetKind) {
            throw new IllegalArgumentException(type + " 알림은 " + type.targetKind() + "로만 이동할 수 있습니다.");
        }
        // MySQL VARCHAR 길이는 문자(코드포인트) 단위다. 이모지가 둘로 세어지지 않도록 String.length()를 쓰지 않는다.
        if (title == null || title.isBlank() || title.codePointCount(0, title.length()) > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("알림 문구는 1자 이상 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (content != null && content.codePointCount(0, content.length()) > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("알림 내용은 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (targetKind.requiresTargetId() != (targetId != null)) {
            throw new IllegalArgumentException(targetKind.requiresTargetId()
                    ? targetKind + " 알림에는 대상 ID가 필요합니다."
                    : targetKind + " 알림에는 대상 ID를 넣을 수 없습니다.");
        }
        return new Notification(userId, type, title, content, targetKind, targetId);
    }

    // 이미 읽은 알림에 다시 호출해도 그대로다.
    public void markRead() {
        this.read = true;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }
}
