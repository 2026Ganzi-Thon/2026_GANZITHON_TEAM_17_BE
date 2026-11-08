package com.ganzithon.wool.notification.service;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.exception.NotificationErrorCode;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest extends IntegrationTestSupport {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 테스트마다 다른 수신자를 써서 서로의 데이터에 영향받지 않는다.
    private static String newUserId() {
        return UUID.randomUUID().toString();
    }

    private Notification send(String userId, String title) {
        return notificationService.send(userId, NotificationType.THANKS_ARRIVED, title, null, TargetKind.RECEIVED, null);
    }

    @Test
    void 보낸_알림은_저장되어_다시_읽어도_값이_유지된다() {
        String userId = newUserId();

        Notification sent = notificationService.send(userId, NotificationType.REPLY_ARRIVED,
                "포근한 양 312 선배님이 답장을 보냈어요 💌", "내용", TargetKind.REPLY, 501L);

        Notification found = notificationRepository.findById(sent.getId()).orElseThrow();
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getType()).isEqualTo(NotificationType.REPLY_ARRIVED);
        assertThat(found.getTitle()).isEqualTo("포근한 양 312 선배님이 답장을 보냈어요 💌");
        assertThat(found.getContent()).isEqualTo("내용");
        assertThat(found.getTargetKind()).isEqualTo(TargetKind.REPLY);
        assertThat(found.getTargetId()).isEqualTo(501L);
        assertThat(found.isRead()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void 내_알림만_최신순으로_돌려준다() {
        String me = newUserId();
        String other = newUserId();
        Notification first = send(me, "첫 번째");
        send(other, "남의 알림");
        Notification second = send(me, "두 번째");
        Notification third = send(me, "세 번째");

        List<Notification> mine = notificationService.getMyNotifications(me);

        assertThat(mine).extracting(Notification::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void 생성_시각이_같으면_ID가_큰_알림이_먼저다() {
        String me = newUserId();
        // 생성 시각을 직접 정해야 하므로 같은 created_at의 행을 SQL로 넣는다. (저장 순서와 다른 순서로 ID가 붙지 않도록 차례로 넣는다)
        for (String title : List.of("가", "나", "다")) {
            jdbcTemplate.update("INSERT INTO notifications (user_id, type, title, target_kind, is_read, created_at) "
                    + "VALUES (?, 'THANKS_ARRIVED', ?, 'RECEIVED', FALSE, '2026-11-08 10:00:00.000000')", me, title);
        }

        List<Notification> mine = notificationService.getMyNotifications(me);

        assertThat(mine).extracting(Notification::getTitle).containsExactly("다", "나", "가");
        // 정렬 기준이 되는 생성 시각이 세 행 모두 실제로 같다는 전제도 확인한다.
        assertThat(mine).hasSize(3);
        assertThat(mine).extracting(Notification::getCreatedAt).containsOnly(mine.get(0).getCreatedAt());
    }

    @Test
    void 생성_시각이_다르면_ID와_상관없이_최신_시각이_먼저다() {
        String me = newUserId();
        jdbcTemplate.update("INSERT INTO notifications (user_id, type, title, target_kind, is_read, created_at) "
                + "VALUES (?, 'THANKS_ARRIVED', '나중에 만든 것 같지만 오래된 알림', 'RECEIVED', FALSE, '2026-11-01 10:00:00.000000')", me);
        jdbcTemplate.update("INSERT INTO notifications (user_id, type, title, target_kind, is_read, created_at) "
                + "VALUES (?, 'THANKS_ARRIVED', '최근 알림', 'RECEIVED', FALSE, '2026-11-08 10:00:00.000000')", me);

        assertThat(notificationService.getMyNotifications(me)).extracting(Notification::getTitle)
                .containsExactly("최근 알림", "나중에 만든 것 같지만 오래된 알림");
    }

    @Test
    void 바깥_트랜잭션이_롤백되면_알림도_함께_취소된다() {
        String userId = newUserId();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            send(userId, "함께 취소될 알림");
            // 이 안에서는 같은 트랜잭션이라 방금 저장한 알림이 보인다.
            assertThat(notificationService.getMyNotifications(userId)).hasSize(1);
            throw new IllegalStateException("답장 저장 실패를 흉내 낸 예외");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(notificationService.getMyNotifications(userId)).isEmpty();
    }

    @Test
    void 바깥_트랜잭션이_커밋되면_알림도_함께_저장된다() {
        String userId = newUserId();

        transactionTemplate.executeWithoutResult(status -> send(userId, "함께 저장될 알림"));

        assertThat(notificationService.getMyNotifications(userId)).hasSize(1);
    }

    @Test
    void 알림이_없으면_빈_목록이다() {
        assertThat(notificationService.getMyNotifications(newUserId())).isEmpty();
    }

    @Test
    void 읽음_처리하면_저장된다() {
        String userId = newUserId();
        Notification sent = send(userId, "읽을 알림");

        Notification result = notificationService.markRead(userId, sent.getId());

        assertThat(result.isRead()).isTrue();
        assertThat(notificationRepository.findById(sent.getId()).orElseThrow().isRead()).isTrue();
    }

    @Test
    void 이미_읽은_알림을_다시_읽음_처리해도_같은_결과다() {
        String userId = newUserId();
        Notification sent = send(userId, "읽을 알림");
        notificationService.markRead(userId, sent.getId());

        Notification again = notificationService.markRead(userId, sent.getId());

        assertThat(again.isRead()).isTrue();
    }

    @Test
    void 읽음_처리는_다른_알림에_영향을_주지_않는다() {
        String userId = newUserId();
        Notification target = send(userId, "대상");
        Notification untouched = send(userId, "그대로");

        notificationService.markRead(userId, target.getId());

        assertThat(notificationRepository.findById(untouched.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void 존재하지_않는_알림은_404로_알린다() {
        assertThatThrownBy(() -> notificationService.markRead(newUserId(), Long.MAX_VALUE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    void 남의_알림은_403으로_알리고_읽음_처리하지_않는다() {
        Notification sent = send(newUserId(), "남의 알림");

        assertThatThrownBy(() -> notificationService.markRead(newUserId(), sent.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCode.FORBIDDEN_NOTIFICATION));
        assertThat(notificationRepository.findById(sent.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void 에러_코드는_명세와_같다() {
        assertThat(NotificationErrorCode.FORBIDDEN_NOTIFICATION.getCode()).isEqualTo("NOTI_403_001");
        assertThat(NotificationErrorCode.FORBIDDEN_NOTIFICATION.getMessage()).isEqualTo("볼 수 없는 알림입니다.");
        assertThat(NotificationErrorCode.FORBIDDEN_NOTIFICATION.getHttpStatus().value()).isEqualTo(403);
        assertThat(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getCode()).isEqualTo("NOTI_404_001");
        assertThat(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getMessage()).isEqualTo("알림을 찾을 수 없습니다.");
        assertThat(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getHttpStatus().value()).isEqualTo(404);
    }
}
