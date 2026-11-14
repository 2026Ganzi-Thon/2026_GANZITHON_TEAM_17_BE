package com.ganzithon.wool.notification.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.notification.response.NotificationListResponse;
import com.ganzithon.wool.notification.response.NotificationReadResponse;
import com.ganzithon.wool.notification.response.NotificationResponseCode;
import com.ganzithon.wool.notification.service.NotificationService;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(@LoginUser User user) {
        return ApiResponse.of(NotificationResponseCode.LIST,
                NotificationListResponse.from(notificationService.getMyNotifications(user.getId()))).toResponseEntity();
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markRead(
            @LoginUser User user, @PathVariable Long notificationId) {
        return ApiResponse.of(NotificationResponseCode.READ,
                NotificationReadResponse.from(notificationService.markRead(user.getId(), notificationId)))
                .toResponseEntity();
    }
}
