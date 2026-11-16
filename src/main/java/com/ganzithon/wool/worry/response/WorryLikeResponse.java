package com.ganzithon.wool.worry.response;

public record WorryLikeResponse(Long worryId, long likeCount, boolean likedByMe) {
}
