package com.ganzithon.wool.worry.response;

import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;

import java.time.LocalDateTime;
import java.util.List;

public record WorryDetailResponse(Long worryId, String title, String content, WorryTag tag,
                                  WorryVisibility visibility, Person author, boolean isMine, boolean canEdit,
                                  boolean hidden, long likeCount, boolean likedByMe, List<ReplyItem> replies,
                                  LocalDateTime createdAt) {
    public record Person(String nickname, AgeGroup ageGroup) {}
    public record FollowUp(String content, String visibility, LocalDateTime createdAt) {}
    public record ReplyItem(Long replyId, ReplyType type, String content, String imageUrl, Person senior,
                            long likeCount, boolean likedByMe, boolean isNew, FollowUp followUp,
                            LocalDateTime createdAt) {}
}
