package com.ganzithon.wool.feedback.response;

import com.ganzithon.wool.global.response.ResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum FollowUpResponseCode implements ResponseCode {
    VISIBILITY_CHANGED(HttpStatus.OK, "FOLLOWUP_200_1", "후일담 공개 범위를 변경했습니다."),
    CREATED(HttpStatus.CREATED, "FOLLOWUP_201_1", "선배님께 그 뒤 이야기를 전했어요.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
