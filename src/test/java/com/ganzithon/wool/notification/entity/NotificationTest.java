package com.ganzithon.wool.notification.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void 만들면_읽지_않은_상태이고_값이_그대로_담긴다() {
        Notification notification = Notification.of(USER_ID, NotificationType.REPLY_ARRIVED,
                "포근한 양 312 선배님이 답장을 보냈어요 💌", null, TargetKind.REPLY, 501L);

        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getUserId()).isEqualTo(USER_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.REPLY_ARRIVED);
        assertThat(notification.getTitle()).isEqualTo("포근한 양 312 선배님이 답장을 보냈어요 💌");
        assertThat(notification.getContent()).isNull();
        assertThat(notification.getTargetKind()).isEqualTo(TargetKind.REPLY);
        assertThat(notification.getTargetId()).isEqualTo(501L);
    }

    @Test
    void 읽음_처리는_여러_번_해도_읽은_상태다() {
        Notification notification = Notification.of(USER_ID, NotificationType.THANKS_ARRIVED, "마음이 도착했어요", null,
                TargetKind.RECEIVED, null);

        notification.markRead();
        notification.markRead();

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void 수신자인지_확인한다() {
        Notification notification = Notification.of(USER_ID, NotificationType.SANCTION, "경고를 받았어요", null,
                TargetKind.SANCTION, null);

        assertThat(notification.isOwnedBy(USER_ID)).isTrue();
        assertThat(notification.isOwnedBy("22222222-2222-2222-2222-222222222222")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TargetKind.class, names = {"REPLY", "CERTIFICATE"})
    void 답장과_감사장_알림은_대상_ID가_있어야_한다(TargetKind kind) {
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.REPLY_ARRIVED, "제목", null, kind, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> Notification.of(USER_ID, NotificationType.REPLY_ARRIVED, "제목", null, kind, 1L))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = TargetKind.class, names = {"RECEIVED", "MY_REPLIES", "SANCTION"})
    void 나머지_알림은_대상_ID를_넣을_수_없다(TargetKind kind) {
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.THANKS_ARRIVED, "제목", null, kind, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> Notification.of(USER_ID, NotificationType.THANKS_ARRIVED, "제목", null, kind, null))
                .doesNotThrowAnyException();
    }

    @Test
    void 수신자_문구_종류가_없거나_길이를_넘으면_거부한다() {
        assertThatThrownBy(() -> Notification.of(null, NotificationType.SANCTION, "제목", null, TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(" ", NotificationType.SANCTION, "제목", null, TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, null, "제목", null, TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, "제목", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, null, null, TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, " ", null, TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, "가".repeat(256), null, TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, "제목", "가".repeat(1001), TargetKind.SANCTION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문구_255자_내용_1000자는_허용한다() {
        assertThatCode(() -> Notification.of(USER_ID, NotificationType.SANCTION, "가".repeat(255), "나".repeat(1000),
                TargetKind.SANCTION, null)).doesNotThrowAnyException();
    }
}
