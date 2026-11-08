package com.ganzithon.wool.notification.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

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

    // 이동 대상 종류에 맞는 알림 종류를 고른다. (종류마다 이동 화면이 하나로 정해져 있다)
    private static NotificationType typeFor(TargetKind kind) {
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.targetKind() == kind)
                .findFirst().orElseThrow();
    }

    @ParameterizedTest
    @EnumSource(value = TargetKind.class, names = {"REPLY", "CERTIFICATE"})
    void 답장과_감사장_알림은_대상_ID가_있어야_한다(TargetKind kind) {
        NotificationType type = typeFor(kind);

        assertThatThrownBy(() -> Notification.of(USER_ID, type, "제목", null, kind, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> Notification.of(USER_ID, type, "제목", null, kind, 1L))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = TargetKind.class, names = {"RECEIVED", "SANCTION"})
    void 받은_마음과_제재_알림은_대상_ID를_넣을_수_없다(TargetKind kind) {
        NotificationType type = typeFor(kind);

        assertThatThrownBy(() -> Notification.of(USER_ID, type, "제목", null, kind, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> Notification.of(USER_ID, type, "제목", null, kind, null))
                .doesNotThrowAnyException();
    }

    @Test
    void 알림_종류는_명세에_정한_이동_화면으로만_만들_수_있다() {
        assertThat(NotificationType.REPLY_ARRIVED.targetKind()).isEqualTo(TargetKind.REPLY);
        assertThat(NotificationType.FOLLOW_UP_REMINDER.targetKind()).isEqualTo(TargetKind.REPLY);
        assertThat(NotificationType.THANKS_ARRIVED.targetKind()).isEqualTo(TargetKind.RECEIVED);
        assertThat(NotificationType.FOLLOW_UP_ARRIVED.targetKind()).isEqualTo(TargetKind.RECEIVED);
        assertThat(NotificationType.SANCTION.targetKind()).isEqualTo(TargetKind.SANCTION);
        assertThat(NotificationType.CERTIFICATE_ISSUED.targetKind()).isEqualTo(TargetKind.CERTIFICATE);
    }

    @Test
    void 종류와_이동_화면의_모든_조합에서_짝이_맞는_조합만_허용한다() {
        for (NotificationType type : NotificationType.values()) {
            for (TargetKind kind : TargetKind.values()) {
                Long targetId = kind.requiresTargetId() ? 1L : null;
                if (type.targetKind() == kind) {
                    assertThatCode(() -> Notification.of(USER_ID, type, "제목", null, kind, targetId))
                            .as("%s + %s", type, kind).doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> Notification.of(USER_ID, type, "제목", null, kind, targetId))
                            .as("%s + %s", type, kind).isInstanceOf(IllegalArgumentException.class);
                }
            }
        }
    }

    @Test
    void 답장_도착_알림이_제재_안내로_이동하는_잘못된_조합은_거부한다() {
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.REPLY_ARRIVED, "답장이 도착했어요", null,
                TargetKind.SANCTION, null)).isInstanceOf(IllegalArgumentException.class);
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

    // 이모지(보조 평면 문자)는 String.length()로는 2지만 MySQL VARCHAR에서는 1자다.
    private static final String EMOJI = "💌";

    @Test
    void 이모지는_한_글자로_세어_255자_문구와_1000자_내용을_허용한다() {
        assertThat(EMOJI.length()).isEqualTo(2);

        assertThatCode(() -> Notification.of(USER_ID, NotificationType.SANCTION, EMOJI.repeat(255), EMOJI.repeat(1000),
                TargetKind.SANCTION, null)).doesNotThrowAnyException();
        assertThatCode(() -> Notification.of(USER_ID, NotificationType.SANCTION, EMOJI.repeat(128) + "가".repeat(127), null,
                TargetKind.SANCTION, null)).doesNotThrowAnyException();
    }

    @Test
    void 이모지를_포함해도_256자_문구와_1001자_내용은_거부한다() {
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, EMOJI.repeat(256), null,
                TargetKind.SANCTION, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, EMOJI.repeat(255) + "가", null,
                TargetKind.SANCTION, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notification.of(USER_ID, NotificationType.SANCTION, "제목", EMOJI.repeat(1001),
                TargetKind.SANCTION, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
