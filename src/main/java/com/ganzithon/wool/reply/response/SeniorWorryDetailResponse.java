package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;

import java.time.LocalDateTime;
import java.util.List;

// REPLY-009. 청년의 닉네임은 내려주지 않고 나이대만 보여준다.
public record SeniorWorryDetailResponse(Long worryId, String title, String content, WorryTag tag,
                                        WorryVisibility visibility, AgeGroup authorAgeGroup,
                                        boolean repliedByMe, boolean canReply, LocalDateTime restrictedUntil,
                                        List<OtherReply> otherReplies, LocalDateTime createdAt) {

    public record Senior(String nickname, AgeGroup ageGroup) {
    }

    public record OtherReply(Long replyId, ReplyType type, String content, String imageUrl, Senior senior,
                             LocalDateTime createdAt) {

        public static OtherReply from(Reply reply) {
            return new OtherReply(reply.getId(), reply.getType(), reply.getContent(), reply.getImageUrl(),
                    new Senior(reply.getSenior().getNickname(), reply.getSenior().getAgeGroup()), reply.getCreatedAt());
        }
    }

    public static SeniorWorryDetailResponse of(Worry worry, boolean repliedByMe, boolean canReply,
                                               LocalDateTime restrictedUntil, List<OtherReply> otherReplies) {
        return new SeniorWorryDetailResponse(worry.getId(), worry.getTitle(), worry.getContent(), worry.getTag(),
                worry.getVisibility(), worry.getAuthor().getAgeGroup(), repliedByMe, canReply, restrictedUntil,
                otherReplies, worry.getCreatedAt());
    }
}
