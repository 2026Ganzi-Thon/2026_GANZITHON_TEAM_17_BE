package com.ganzithon.wool.notification.response;

import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(List<Item> items) {

    public static NotificationListResponse from(List<Notification> notifications) {
        return new NotificationListResponse(notifications.stream().map(Item::from).toList());
    }

    public record Item(Long notificationId, NotificationType type, String title, Target target,
                       boolean read, LocalDateTime createdAt) {
        private static Item from(Notification notification) {
            return new Item(notification.getId(), notification.getType(), notification.getTitle(),
                    new Target(notification.getTargetKind(), notification.getTargetId()),
                    notification.isRead(), notification.getCreatedAt());
        }
    }

    public record Target(TargetKind kind, Long id) {
    }
}
