package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.filter.service.LetterImageChecker;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.global.storage.FileStorage;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.reply.response.CreateReplyResponse;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 답장 작성(REPLY-005). 검사 순서: 요청 형식(400) → 고민(404) → 제재(403) → 이미 답장(409) → 내용 검사(422).
// 손편지는 사진 속 글자를 읽어 검사하는데(외부 호출이라 몇 초 걸린다), DB 잠금을 쥔 채 기다리면 같은 어르신·고민의
// 다른 요청이 막히므로 트랜잭션 밖에서 한다. 그래서 잠금 없는 사전 확인(404/403/409) → 사진 검사(422) → 트랜잭션 순서다.
// 트랜잭션(ReplyCreateTransaction)이 잠금을 잡고 모든 확인을 다시 하므로 최종 판단은 그쪽이다.
@Service
@RequiredArgsConstructor
public class ReplyCreateService {

    private final ReplyCreateTransaction transaction;
    private final FileStorage fileStorage;
    private final LetterImageChecker letterImageChecker;

    public CreateReplyResponse create(User requester, Long worryId, CreateReplyRequest request) {
        if (request.type() == ReplyType.LETTER) {
            // 손편지는 업로드 API가 실제로 저장한 사진만 받는다. 형식만 맞는 가짜 주소는 가장 먼저 돌려보낸다.
            if (!fileStorage.exists(request.imageUrl())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            transaction.validate(requester, worryId);
            letterImageChecker.check(request.imageUrl());
        }
        return transaction.create(requester, worryId, request);
    }
}
