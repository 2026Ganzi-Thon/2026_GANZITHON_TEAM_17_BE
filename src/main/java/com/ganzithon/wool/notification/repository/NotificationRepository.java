package com.ganzithon.wool.notification.repository;

import com.ganzithon.wool.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 최신 알림이 먼저. 같은 시각이면 나중에 만든 것이 먼저.
    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(String userId);
}
