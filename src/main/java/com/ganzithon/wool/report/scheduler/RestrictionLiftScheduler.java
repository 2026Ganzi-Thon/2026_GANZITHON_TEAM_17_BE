package com.ganzithon.wool.report.scheduler;

import com.ganzithon.wool.report.service.RestrictionLiftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 1분마다 기간이 지난 7일 답장 제한을 푼다. wool.scheduling.enabled=false면 만들지 않는다. (테스트에서 끈다)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wool.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class RestrictionLiftScheduler {

    private final RestrictionLiftService restrictionLiftService;

    // fixedDelay: 이전 실행이 끝난 뒤 1분. 서버가 여러 대로 늘어도 한 어르신은 행 잠금과 재확인 때문에 한 번만 풀린다.
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        int lifted = restrictionLiftService.liftExpired(LocalDateTime.now());
        if (lifted > 0) {
            log.info("7일 답장 제한 해제 {}명", lifted);
        }
    }
}
