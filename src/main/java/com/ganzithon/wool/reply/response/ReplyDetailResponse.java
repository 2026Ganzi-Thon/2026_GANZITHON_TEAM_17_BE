package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.worry.entity.WorryVisibility;

import java.time.LocalDateTime;
import java.util.List;

// FEEDBACK-001. 고민 작성자(청년)가 보는 답장 상세.
// content는 LETTER면 null, imageUrl은 LETTER가 아니면 null이다. thanks/followUp은 보내지 않았으면 null이다.
public record ReplyDetailResponse(Long replyId, Long worryId, ReplyType type, String content, String imageUrl,
                                  Senior senior, boolean isFirstView, List<Action> availableActions,
                                  Thanks thanks, FollowUp followUp, LocalDateTime createdAt) {

    public enum Action { THANKS, FOLLOW_UP }

    public record Senior(String nickname, AgeGroup ageGroup) {}

    public record Thanks(ThanksStamp stamp, String message, LocalDateTime createdAt) {}

    public record FollowUp(String content, WorryVisibility visibility, LocalDateTime createdAt) {}
}
