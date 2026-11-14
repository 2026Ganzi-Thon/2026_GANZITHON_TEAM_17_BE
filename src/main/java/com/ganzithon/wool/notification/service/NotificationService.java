package com.ganzithon.wool.notification.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.exception.NotificationErrorCode;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 앱 안의 알림 목록만 다룬다. (푸시 알림은 해커톤 제외 범위, POL-NTF-04)
// 알림을 만드는 쪽(답장, 고마움, 후일담, 제재, 감사장, 스케줄러)이 send를 호출하고,
// 알림 목록 조회·읽음 처리 API(NOTI-001·002)가 나머지 두 메서드를 쓴다.
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 호출한 쪽에 트랜잭션이 있으면 거기에 참여하고(답장 저장과 알림 저장이 같이 커밋되거나 같이 롤백된다),
    // 없으면 이 메서드가 새 트랜잭션을 연다. 다른 Bean에서 호출해야 트랜잭션이 적용된다.
    @Transactional
    public Notification send(String userId, NotificationType type, String title, String content,
                             TargetKind targetKind, Long targetId) {
        return notificationRepository.save(Notification.of(userId, type, title, content, targetKind, targetId));
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    // 이미 읽은 알림도 같은 결과로 성공한다.
    @Transactional
    public Notification markRead(String userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(userId)) {
            throw new BusinessException(NotificationErrorCode.FORBIDDEN_NOTIFICATION);
        }
        notification.markRead();
        return notification;
    }
}
