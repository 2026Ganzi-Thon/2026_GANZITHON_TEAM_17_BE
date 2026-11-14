package com.ganzithon.wool.report.service;

import com.ganzithon.wool.reply.service.SeniorLock;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 어르신 한 명의 7일 제한을 푼다. 어르신마다 트랜잭션을 따로 열어서 한 명이 실패해도 나머지는 풀린다.
// 신고(ReplyReportService)와 같은 어르신 행 잠금을 쓴다. 잠금을 잡은 뒤 상태를 다시 읽어서 확인하므로,
// 후보를 고른 뒤 신고가 쌓여 영구 제한이 된 어르신은 풀지 않는다.
@Service
@RequiredArgsConstructor
public class RestrictionLifter {

    private final SeniorLock seniorLock;

    // 잠금을 잡은 뒤 최신 상태를 읽어야 하므로 READ COMMITTED로 실행한다. (ReplyReportService와 같은 이유)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean liftIfExpired(String seniorId, LocalDateTime now) {
        User senior = seniorLock.lock(seniorId);
        if (senior.getSanctionLevel() != SanctionLevel.RESTRICTED
                || senior.getRestrictedUntil() == null
                || senior.getRestrictedUntil().isAfter(now)) {
            return false;
        }
        // 누적 신고 수는 reply_reports에서 계산하므로 상태만 푼다. (POL-RPT-06)
        senior.liftRestriction();
        return true;
    }
}
