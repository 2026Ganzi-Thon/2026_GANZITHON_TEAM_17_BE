package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.reply.entity.WoolStage;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.repository.SeniorWorryRepository;
import com.ganzithon.wool.reply.response.SeniorHomeResponse;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.wool.repository.ReceivedHeartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 어르신 홈 요약(REPLY-001). 읽기만 하고 아무것도 읽음 처리하지 않는다. (받은 마음은 WOOL-002를 조회할 때 읽음 처리된다)
// - 오늘 새 고민 수: 오늘 0시(서버 시간대, 한국 시간) 이후에 올라온, 어르신이 읽을 수 있는 고민.
// - 안 읽은 받은 마음 수: 받은 마음 목록(WOOL-002)과 같은 조건으로 센다.
// - 털 단계와 누적 답장 수: 털 기록(WOOL-001)과 같은 계산이다.
@Service
@RequiredArgsConstructor
public class SeniorHomeService {

    private final SeniorWorryRepository seniorWorryRepository;
    private final ReceivedHeartRepository receivedHeartRepository;
    private final ReplyRepository replyRepository;

    @Transactional(readOnly = true)
    public SeniorHomeResponse get(User senior) {
        // 고민의 createdAt은 LocalDateTime.now()로 저장되므로 같은 기준(서버 시간대)으로 오늘 0시를 구한다.
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        long totalReplies = replyRepository.countVisibleBySeniorId(senior.getId());
        return new SeniorHomeResponse(
                senior.getNickname(),
                seniorWorryRepository.countReadableCreatedSince(startOfToday),
                receivedHeartRepository.countUnreadBySeniorId(senior.getId()),
                WoolStage.of(totalReplies),
                totalReplies);
    }
}
