package com.ganzithon.wool.reply.response;

import java.time.LocalDateTime;

// updatedAt은 답장을 마지막으로 고친 시각이다.
public record UpdateReplyResponse(Long replyId, LocalDateTime updatedAt) {
}
