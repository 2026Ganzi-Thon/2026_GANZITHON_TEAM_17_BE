package com.ganzithon.wool.report.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

// 해제에 계속 실패하는 어르신이 앞쪽 후보를 차지해도 뒤쪽 후보가 밀리지 않는지 확인한다.
class RestrictionLiftFailureTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private RestrictionLiftService restrictionLiftService;
    @MockitoSpyBean private RestrictionLifter restrictionLifter;
    @Autowired private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanUp() {
        databaseCleaner.clean();
    }

    @Test
    void 앞쪽_후보_백_명이_모두_실패해도_뒤쪽_후보는_풀린다() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<User> all = new ArrayList<>();
        for (int i = 0; i < RestrictionLiftService.BATCH_SIZE + 5; i++) {
            User senior = User.join(Role.SENIOR, AgeGroup.AGE_60S, "실패" + UUID.randomUUID().toString().substring(0, 6));
            senior.applySanction(SanctionLevel.RESTRICTED, now.minusMinutes(1));
            all.add(users.saveAndFlush(senior));
        }
        // 후보는 id 순서로 읽으므로, id가 앞선 100명을 모두 실패하게 만든다.
        List<String> idsInOrder = all.stream().map(User::getId).sorted().toList();
        List<String> failing = idsInOrder.subList(0, RestrictionLiftService.BATCH_SIZE);
        for (String id : failing) {
            doThrow(new IllegalStateException("해제 실패")).when(restrictionLifter).liftIfExpired(eq(id), any());
        }

        int lifted = restrictionLiftService.liftExpired(now);

        assertThat(lifted).isEqualTo(5);
        for (String id : idsInOrder.subList(RestrictionLiftService.BATCH_SIZE, idsInOrder.size())) {
            assertThat(users.findById(id).orElseThrow().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        }
        for (String id : failing) {
            assertThat(users.findById(id).orElseThrow().getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
        }
    }
}
