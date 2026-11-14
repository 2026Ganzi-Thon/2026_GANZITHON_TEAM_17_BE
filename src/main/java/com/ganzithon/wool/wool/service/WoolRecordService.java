package com.ganzithon.wool.wool.service;

import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.wool.response.WoolRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 털 기록 조회(WOOL-001). 누적 답장 수는 숨김·삭제 답장을 뺀 유효 답장 수이고(POL-WOL-01), 읽기만 한다.
@Service
@RequiredArgsConstructor
public class WoolRecordService {

    private final ReplyRepository replyRepository;

    @Transactional(readOnly = true)
    public WoolRecordResponse get(User senior) {
        return WoolRecordResponse.from(replyRepository.countVisibleBySeniorId(senior.getId()));
    }
}
