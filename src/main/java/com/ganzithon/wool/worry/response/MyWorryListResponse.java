package com.ganzithon.wool.worry.response;

import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import java.time.LocalDateTime;
import java.util.List;

public record MyWorryListResponse(List<Item> items) {
    public record Item(Long worryId, String title, WorryTag tag, WorryVisibility visibility,
                       long replyCount, boolean hasUnreadReply, boolean hidden, LocalDateTime createdAt) {
    }
}
