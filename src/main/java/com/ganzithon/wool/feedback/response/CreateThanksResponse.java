package com.ganzithon.wool.feedback.response;

import com.ganzithon.wool.feedback.entity.ThanksStamp;
import java.time.LocalDateTime;

public record CreateThanksResponse(Long replyId, ThanksStamp stamp, String message, LocalDateTime createdAt) {
}
