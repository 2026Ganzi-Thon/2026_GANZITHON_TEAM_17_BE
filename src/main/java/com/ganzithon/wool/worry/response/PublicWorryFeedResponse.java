package com.ganzithon.wool.worry.response;

import com.ganzithon.wool.worry.entity.WorryTag;
import java.time.LocalDateTime;
import java.util.List;

public record PublicWorryFeedResponse(List<Item> items) {
    public record Item(Long worryId, String title, WorryTag tag, long replyCount, long likeCount,
                       LocalDateTime createdAt) {
    }
}
