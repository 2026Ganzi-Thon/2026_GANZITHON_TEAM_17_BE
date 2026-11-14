package com.ganzithon.wool.notification.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationResponseCode implements ResponseCode {
    LIST(HttpStatus.OK, "NOTI_200_1", "알림 목록 조회에 성공했습니다."),
    READ(HttpStatus.OK, "NOTI_200_2", "알림을 읽음 처리했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
