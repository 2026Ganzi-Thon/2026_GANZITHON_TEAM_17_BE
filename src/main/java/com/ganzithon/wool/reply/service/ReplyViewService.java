package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.feedback.entity.FollowUp;
import com.ganzithon.wool.feedback.entity.Thanks;
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.response.ReplyDetailResponse;
import com.ganzithon.wool.reply.response.ReplyDetailResponse.Action;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.repository.WorryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 답장 상세 조회(FEEDBACK-001). 고민 작성자가 열어보면 최초 조회 시각을 남긴다. (안 읽은 답장 표시가 이 값을 쓴다)
// 고마움·후일담·답장 삭제와 같은 순서(고민 → 답장)로 잠가서, 같은 답장을 동시에 처음 열어도 "첫 조회"는 한 번만 나온다.
@Service
@RequiredArgsConstructor
public class ReplyViewService {

    private final WorryRepository worries;
    private final ReplyRepository replies;
    private final ThanksRepository thanksRepository;
    private final FollowUpRepository followUpRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReplyDetailResponse view(String userId, Long replyId) {
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

        boolean firstView = reply.getFirstViewedAt() == null;
        reply.markFirstViewed(LocalDateTime.now());

        Thanks thanks = thanksRepository.findByReplyId(replyId).orElse(null);
        FollowUp followUp = followUpRepository.findByReplyId(replyId).orElse(null);
        return new ReplyDetailResponse(reply.getId(), worryId, reply.getType(), reply.getContent(), reply.getImageUrl(),
                new ReplyDetailResponse.Senior(reply.getSenior().getNickname(), reply.getSenior().getAgeGroup()),
                firstView, availableActions(firstView, thanks != null, followUp != null),
                thanks == null ? null : new ReplyDetailResponse.Thanks(thanks.getStamp(), thanks.getMessage(), thanks.getCreatedAt()),
                followUp == null ? null : new ReplyDetailResponse.FollowUp(followUp.getContent(), followUp.getVisibility(), followUp.getCreatedAt()),
                reply.getCreatedAt());
    }

    // 첫 조회는 고마움만, 다시 들어오면 아직 안 보낸 것만 보여준다. 둘 다 보냈으면 비어 있다.
    private static List<Action> availableActions(boolean firstView, boolean thanksSent, boolean followUpSent) {
        List<Action> actions = new ArrayList<>();
        if (!thanksSent) actions.add(Action.THANKS);
        if (!firstView && !followUpSent) actions.add(Action.FOLLOW_UP);
        return actions;
    }
}
