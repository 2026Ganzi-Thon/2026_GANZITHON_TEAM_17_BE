package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyLikeRepository;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.response.ReplyLikeResponse;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplyLikeService {
    private final ReplyRepository replies;
    private final WorryRepository worries;
    private final ReplyLikeRepository likes;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReplyLikeResponse toggle(String userId, Long replyId) {
        Long worryId = replies.findWorryIdForLike(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        var worry = worries.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        var reply = replies.findByIdAndDeletedAtIsNull(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        if (worry.isHidden()) throw new BusinessException(ReplyErrorCode.NOT_FOUND);
        if (worry.getVisibility() != WorryVisibility.PUBLIC || reply.isHidden()) {
            throw new BusinessException(ReplyErrorCode.FORBIDDEN_VIEW);
        }
        boolean liked = likes.delete(replyId, userId) == 0;
        if (liked) likes.insert(replyId, userId);
        return new ReplyLikeResponse(replyId, likes.count(replyId), liked);
    }
}
