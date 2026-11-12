package com.ganzithon.wool.user.service;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.exception.UserErrorCode;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 서로 다른 두 사용자가 "같은 닉네임"으로 동시에 바꾸는 상황을 실제 스레드 두 개로 실행한다. (검증기를 건드리지 않는다)
// 어떤 시점에 겹치든 결과는 같아야 한다: 한 명만 성공하고 한 명은 409이며, DB에는 그 닉네임을 가진 사용자가 정확히 한 명이다.
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
class NicknameUpdateConcurrencyTest extends IntegrationTestSupport {

    private static final int ROUNDS = 25;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void 두_사용자가_같은_닉네임으로_동시에_바꾸면_한_명만_성공하고_한_명은_409이다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                User first = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, unique("가")));
                User second = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, unique("나")));
                String target = unique("동시");

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<String>> results = new ArrayList<>();
                for (User user : List.of(first, second)) {
                    Callable<String> task = () -> {
                        ready.countDown();
                        go.await();
                        try {
                            userService.updateNickname(user, target);
                            return "성공";
                        } catch (BusinessException e) {
                            return e.getErrorCode() == UserErrorCode.NICKNAME_DUPLICATED ? "409" : "다른 오류: " + e.getErrorCode();
                        }
                    };
                    results.add(pool.submit(task));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();

                List<String> outcomes = new ArrayList<>();
                for (Future<String> result : results) {
                    outcomes.add(result.get(30, TimeUnit.SECONDS));
                }

                assertThat(outcomes).as("%d번째 라운드", round).containsExactlyInAnyOrder("성공", "409");
                long owners = List.of(first, second).stream()
                        .filter(user -> target.equals(userRepository.findById(user.getId()).orElseThrow().getNickname()))
                        .count();
                assertThat(owners).as("%d번째 라운드에서 그 닉네임을 가진 사용자 수", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
