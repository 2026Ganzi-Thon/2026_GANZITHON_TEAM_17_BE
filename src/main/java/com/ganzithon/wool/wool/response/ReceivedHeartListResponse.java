package com.ganzithon.wool.wool.response;

import com.ganzithon.wool.feedback.entity.ThanksStamp;

import java.time.LocalDateTime;
import java.util.List;

// WOOL-002. 청년에게 받은 고마움과 후일담을 최신순으로 모은 목록이다.
// kind가 FOLLOW_UP이면 stamp는 null이고, 고마움에 한마디가 없으면 content가 null이다.
public record ReceivedHeartListResponse(List<Item> items) {

    public enum Kind {
        THANKS, FOLLOW_UP
    }

    public record Item(Kind kind, Long replyId, String worryTitle, ThanksStamp stamp, String content,
                       LocalDateTime createdAt) {
    }
}
