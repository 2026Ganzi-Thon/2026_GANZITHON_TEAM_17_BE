package com.ganzithon.wool.reply.response;

// totalReplies는 삭제 후의 누적 답장 수다. 숨기거나 삭제한 답장은 세지 않는다.
public record DeleteReplyResponse(Long replyId, long totalReplies) {
}
