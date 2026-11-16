package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.reply.entity.ReplyType;

import java.time.LocalDateTime;
import java.util.List;

// REPLY-006. 어르신이 보낸 답장 목록. 답장 내용은 내려주지 않는다. (카드에는 어떤 고민에 보냈는지와 받은 반응만 보인다)
// 고민을 쓴 청년이 고민을 삭제했거나 신고로 숨겨졌으면 worryTitle은 "삭제된 고민"이고 반응은 보이지 않는다. (POL-WRY-09)
public record SeniorReplyListResponse(List<Item> items) {

    public record Item(Long replyId, Long worryId, String worryTitle, ReplyType type, long likeCount,
                       boolean thanked, boolean hasFollowUp, LocalDateTime createdAt) {
    }
}
