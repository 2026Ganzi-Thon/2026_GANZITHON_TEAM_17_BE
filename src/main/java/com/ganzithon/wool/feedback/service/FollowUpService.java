package com.ganzithon.wool.feedback.service;

import com.ganzithon.wool.feedback.entity.FollowUp;
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.feedback.request.CreateFollowUpRequest;
import com.ganzithon.wool.feedback.response.ChangeFollowUpVisibilityResponse;
import com.ganzithon.wool.feedback.response.CreateFollowUpResponse;
import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.service.NotificationService;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class FollowUpService {
    private final ReplyRepository replies;
    private final WorryRepository worries;
    private final FollowUpRepository followUps;
    private final ContentFilter filter;
    private final NotificationService notifications;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateFollowUpResponse create(String userId, Long replyId, CreateFollowUpRequest request) {
        Long worryId = replies.findWorryIdForLike(replyId).orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        var worry = worries.findByIdAndDeletedAtIsNull(worryId).orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        var reply = replies.findByIdAndDeletedAtIsNull(replyId).orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        if (worry.isHidden()) throw new BusinessException(ReplyErrorCode.NOT_FOUND);
        if (reply.isHidden() || !worry.getAuthor().getId().equals(userId)) throw new BusinessException(ReplyErrorCode.FORBIDDEN_VIEW);
        if (followUps.existsByReplyId(replyId)) throw new BusinessException(FollowUpErrorCode.ALREADY_SENT);
        var visibility = request.visibility() == null ? WorryVisibility.SENIORS_ONLY : request.visibility();
        if (worry.getVisibility() == WorryVisibility.SENIORS_ONLY && visibility == WorryVisibility.PUBLIC) throw new BusinessException(FollowUpErrorCode.PRIVATE_WORRY);
        filter.validateLongText(request.content());
        var saved = followUps.saveAndFlush(FollowUp.of(reply, request.content(), visibility));
        notifications.send(reply.getSenior().getId(), NotificationType.FOLLOW_UP_ARRIVED, "후일담이 도착했어요", null, TargetKind.RECEIVED, null);
        return new CreateFollowUpResponse(replyId, saved.getContent(), visibility, saved.getCreatedAt());
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChangeFollowUpVisibilityResponse changeVisibility(String userId, Long replyId, WorryVisibility visibility) {
        Long worryId = replies.findWorryIdForLike(replyId).orElseThrow(() -> new BusinessException(FollowUpErrorCode.NOT_FOUND));
        var worry = worries.findByIdAndDeletedAtIsNull(worryId).orElseThrow(() -> new BusinessException(FollowUpErrorCode.NOT_FOUND));
        var reply = replies.findByIdAndDeletedAtIsNull(replyId).orElseThrow(() -> new BusinessException(FollowUpErrorCode.NOT_FOUND));
        if (worry.isHidden()) throw new BusinessException(FollowUpErrorCode.NOT_FOUND);
        if (reply.isHidden() || !worry.getAuthor().getId().equals(userId)) throw new BusinessException(ReplyErrorCode.FORBIDDEN_VIEW);
        var followUp = followUps.findByReplyId(replyId).orElseThrow(() -> new BusinessException(FollowUpErrorCode.NOT_FOUND));
        if (visibility != WorryVisibility.SENIORS_ONLY || followUp.getVisibility() != WorryVisibility.PUBLIC) {
            throw new BusinessException(FollowUpErrorCode.VISIBILITY_CONFLICT);
        }
        followUp.makeSeniorsOnly();
        return new ChangeFollowUpVisibilityResponse(replyId, followUp.getVisibility());
    }

}
