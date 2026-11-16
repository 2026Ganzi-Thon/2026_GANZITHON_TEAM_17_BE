package com.ganzithon.wool.reply.response;

public record ReplyLikeResponse(Long replyId, long likeCount, boolean likedByMe) {}
