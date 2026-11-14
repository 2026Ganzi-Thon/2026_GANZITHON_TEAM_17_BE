package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;

import java.time.LocalDateTime;
import java.util.List;

// REPLY-002. 본문은 목록에 내려주지 않는다. (시안 카드에 본문 미리보기가 없다)
public record SeniorWorryListResponse(long waitingCount, List<Item> items) {

    public record Item(Long worryId, String title, WorryTag tag, AgeGroup authorAgeGroup, WorryVisibility visibility,
                       long replyCount, boolean repliedByMe, LocalDateTime createdAt) {
    }
}
