package com.ganzithon.wool.report.service;

import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 7일 답장 제한의 자동 해제. (POL-RPT-06) 기간이 지난 어르신의 제재 단계를 NONE으로 되돌린다.
// 읽는 쪽(User.isReplyRestrictedAt)이 만료 시각을 보고 이미 막지 않으므로, 이 작업은 저장된 상태를 맞춰 두는 정리다.
// 이 서비스에는 트랜잭션이 없다. 한 명씩 RestrictionLifter(별도 Bean)가 자기 트랜잭션으로 푼다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RestrictionLiftService {

    // 한 번에 조회하는 후보 수. 한 번의 실행 안에서 id 커서로 끝까지 이어서 처리한다.
    static final int BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final RestrictionLifter restrictionLifter;

    // 이번 실행에서 푼 어르신 수를 돌려준다.
    // 해제에 실패한 사람은 이번 실행에서 건너뛰고(커서가 지나감) 다음 실행에서 다시 후보가 된다.
    // 앞쪽의 실패한 후보가 뒤쪽 후보를 가로막지 않는다.
    public int liftExpired(LocalDateTime now) {
        int lifted = 0;
        String afterId = "";
        while (true) {
            List<String> candidates = userRepository.findIdsRestrictedUntilBefore(
                    SanctionLevel.RESTRICTED, now, afterId, PageRequest.of(0, BATCH_SIZE));
            for (String seniorId : candidates) {
                try {
                    if (restrictionLifter.liftIfExpired(seniorId, now)) {
                        lifted++;
                    }
                } catch (RuntimeException e) {
                    log.warn("7일 제한 해제 실패 seniorId={}", seniorId, e);
                }
            }
            if (candidates.size() < BATCH_SIZE) {
                return lifted;
            }
            afterId = candidates.get(candidates.size() - 1);
        }
    }
}
