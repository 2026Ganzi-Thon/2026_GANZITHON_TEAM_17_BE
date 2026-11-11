package com.ganzithon.wool.notification.response;

import com.ganzithon.wool.notification.entity.Notification;

public record NotificationReadResponse(Long notificationId, boolean read) {
    public static NotificationReadResponse from(Notification notification) {
        return new NotificationReadResponse(notification.getId(), notification.isRead());
    }
}
