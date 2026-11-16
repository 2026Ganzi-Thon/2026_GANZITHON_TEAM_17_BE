package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.response.UpdateReplyResponse;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// 답장 수정(REPLY-008). 청년이 고마움이나 후일담을 보내기 전까지만, 글·음성 답장만 고칠 수 있다.
// 답장 행을 잠근 뒤 판단한다. 고마움·후일담·삭제·신고가 같은 답장 행 잠금을 써서, 고마움을 받은 뒤에 내용이 바뀌는 일이 없다.
// 누적 답장 수를 바꾸지 않아서 어르신 행은 잠그지 않는다. (잠금은 답장 행 하나뿐이라 다른 잠금과 순환하지 않는다)
@Service
@RequiredArgsConstructor
public class ReplyUpdateService {

    private final ReplyRepository replyRepository;
    private final ThanksRepository thanksRepository;
    private final FollowUpRepository followUpRepository;
    private final ContentFilter contentFilter;

    // 잠금을 잡은 뒤 고마움·후일담이 있는지 최신 상태로 읽어야 하므로 READ COMMITTED로 실행한다.
    // MySQL 기본(REPEATABLE READ)에서는 일반 조회가 만든 스냅샷이 잠금을 기다리는 동안의 커밋을 못 볼 수 있다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpdateReplyResponse update(User senior, Long replyId, String content) {
        Reply reply = replyRepository.findByIdAndDeletedAtIsNull(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        if (!reply.isWrittenBy(senior.getId())) {
            throw new BusinessException(ReplyErrorCode.FORBIDDEN_UPDATE);
        }
        // 신고로 숨겨진 답장은 영구히 보이지 않으므로 고칠 수 없다. 없는 것으로 본다.
        if (reply.isHidden()) {
            throw new BusinessException(ReplyErrorCode.NOT_FOUND);
        }
        if (reply.getType() == ReplyType.LETTER) {
            throw new BusinessException(ReplyErrorCode.LETTER_NOT_EDITABLE);
        }
        if (thanksRepository.existsByReplyId(replyId) || followUpRepository.existsByReplyId(replyId)) {
            throw new BusinessException(ReplyErrorCode.FEEDBACK_RECEIVED);
        }
        contentFilter.validateLongText(content);

        reply.editContent(content);
        // 수정 시각(updatedAt)은 저장할 때 채워지므로 먼저 반영한다.
        replyRepository.flush();
        return new UpdateReplyResponse(reply.getId(), reply.getUpdatedAt());
    }
}
