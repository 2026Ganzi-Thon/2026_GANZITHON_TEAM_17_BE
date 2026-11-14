package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.response.DeleteReplyResponse;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 답장 삭제(REPLY-007). 소프트 삭제이고 복구하지 않는다.
// 받은 고마움·후일담·좋아요는 지우지 않는다. 삭제된 답장은 모든 조회에서 빠지므로 함께 보이지 않게 된다.
// 이미 발급한 감사장, 해당 답장의 신고 이력과 제재, 삭제 전의 알림도 그대로 둔다.
// 삭제해도 같은 고민에 다시 답장할 수는 없다. (재답장 방지는 삭제한 답장도 센다)
@Service
@RequiredArgsConstructor
public class ReplyDeleteService {

    private final ReplyRepository replyRepository;
    private final SeniorLock seniorLock;

    @Transactional
    public DeleteReplyResponse delete(User senior, Long replyId) {
        // 답장 행을 잠근 뒤 판단한다. 같은 답장에 대한 수정, 고마움, 신고가 동시에 들어와도 한쪽씩 처리된다.
        Reply reply = replyRepository.findByIdAndDeletedAtIsNull(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        if (!reply.isWrittenBy(senior.getId())) {
            throw new BusinessException(ReplyErrorCode.FORBIDDEN_DELETE);
        }
        // 누적 답장 수가 줄어드는 작업이라 답장 작성과 같은 어르신 행 잠금을 쓴다. 작성과 겹쳐도 누적 수와 감사장 발급이
        // 어느 한쪽이 먼저 끝난 것처럼 맞아떨어진다. 잠금 순서는 답장 → 어르신이다. (답장 작성은 고민 → 어르신)
        seniorLock.lock(senior.getId());
        reply.delete();
        replyRepository.flush();
        return new DeleteReplyResponse(reply.getId(), replyRepository.countVisibleBySeniorId(senior.getId()));
    }
}
