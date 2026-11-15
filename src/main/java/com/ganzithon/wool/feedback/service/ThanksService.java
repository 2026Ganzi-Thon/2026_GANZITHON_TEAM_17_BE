package com.ganzithon.wool.feedback.service;

import com.ganzithon.wool.feedback.entity.Thanks;
import com.ganzithon.wool.feedback.exception.ThanksErrorCode;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.feedback.request.CreateThanksRequest;
import com.ganzithon.wool.feedback.response.CreateThanksResponse;
import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.service.NotificationService;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.repository.WorryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ThanksService {
    private final ReplyRepository replies;
    private final WorryRepository worries;
    private final ThanksRepository thanksRepository;
    private final ContentFilter contentFilter;
    private final NotificationService notifications;

    // 고민 삭제와 답장 삭제·신고에 맞춰 고민 → 답장 순서로 잠근다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateThanksResponse create(String userId, Long replyId, CreateThanksRequest request) {
        Long worryId = replies.findWorryIdForLike(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        Worry worry = worries.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        Reply reply = replies.findByIdAndDeletedAtIsNull(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        if (worry.isHidden()) throw new BusinessException(ReplyErrorCode.NOT_FOUND);
        if (!worry.getAuthor().getId().equals(userId) || reply.isHidden()) {
            throw new BusinessException(ReplyErrorCode.FORBIDDEN_VIEW);
        }
        if (thanksRepository.existsByReplyId(replyId)) {
            throw new BusinessException(ThanksErrorCode.ALREADY_SENT);
        }
        contentFilter.validateShortText(request.message());
        Thanks saved = thanksRepository.saveAndFlush(Thanks.of(reply, request.stamp(), request.message()));
        notifications.send(reply.getSenior().getId(), NotificationType.THANKS_ARRIVED,
                "고마움이 도착했어요", null, TargetKind.RECEIVED, null);
        return new CreateThanksResponse(replyId, saved.getStamp(), saved.getMessage(), saved.getCreatedAt());
    }
}
