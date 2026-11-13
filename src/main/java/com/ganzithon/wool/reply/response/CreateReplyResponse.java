package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.reply.entity.WoolStage;

public record CreateReplyResponse(
        Long replyId,
        long totalReplies,
        WoolStage woolStage,
        boolean certificateIssued,
        Long certificateId
) {
}
