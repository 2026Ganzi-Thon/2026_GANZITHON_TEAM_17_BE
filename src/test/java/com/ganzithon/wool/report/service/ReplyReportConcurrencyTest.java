package com.ganzithon.wool.report.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.report.entity.ReplyReportReason;
import com.ganzithon.wool.report.exception.ReportErrorCode;
import com.ganzithon.wool.report.repository.ReplyReportRepository;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.service.SeniorLock;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 어르신을 두 청년이 동시에 신고해도 누적 신고 수와 제재 알림이 한 번씩만 맞아떨어지는지 확인한다.
// 답장 신고는 답장 행 → 어르신 행 순서로 잠근다. (답장 삭제와 같은 순서, 답장 작성은 고민 행 → 어르신 행)
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
class ReplyReportConcurrencyTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ReplyReportRepository reports;
    @Autowired private NotificationRepository notifications;
    @Autowired private ReplyReportService replyReportService;
    @Autowired private SeniorLock seniorLock;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanUp() {
        databaseCleaner.clean();
    }

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private record Target(User youth, Reply reply) {
    }

    // 청년 한 명이 쓴 고민에 senior가 답장한 상태.
    private Target newTarget(User senior) {
        User youth = newUser(Role.YOUTH, "동신청년");
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        return new Target(youth, replies.saveAndFlush(Reply.text(worry, senior, "답장")));
    }

    private List<Notification> sanctionNotifications(User senior) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).stream()
                .filter(n -> n.getType() == NotificationType.SANCTION).toList();
    }

    // 서로 다른 두 청년이 같은 어르신의 서로 다른 답장을 동시에 신고한다.
    // 잠금이 없으면 둘 다 "내가 첫 신고"라고 보고 경고 알림이 두 번 갈 수 있다.
    @Test
    void 두_청년이_같은_어르신을_동시에_신고해도_경고_알림은_한_번만_간다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                User senior = newUser(Role.SENIOR, "동신선배");
                Target first = newTarget(senior);
                Target second = newTarget(senior);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<String>> results = new ArrayList<>();
                for (Target target : List.of(first, second)) {
                    Callable<String> task = () -> {
                        ready.countDown();
                        go.await();
                        replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.ABUSE);
                        return "ok";
                    };
                    results.add(pool.submit(task));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                for (Future<String> result : results) {
                    assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("ok");
                }

                assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).as("%d번째 라운드", round).isEqualTo(2);
                assertThat(users.findById(senior.getId()).orElseThrow().getSanctionLevel())
                        .as("%d번째 라운드", round).isEqualTo(SanctionLevel.WARNED);
                assertThat(sanctionNotifications(senior)).as("%d번째 라운드", round).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // 같은 청년이 같은 답장을 동시에 신고하면 한 번만 접수되고 나머지는 409다.
    @Test
    void 같은_청년이_같은_답장을_동시에_신고하면_한_번만_접수된다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                User senior = newUser(Role.SENIOR, "동신선배");
                Target target = newTarget(senior);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Callable<String> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.AD);
                        return "접수";
                    } catch (BusinessException e) {
                        return e.getErrorCode() == ReportErrorCode.ALREADY_REPORTED ? "409" : "다른 오류: " + e.getErrorCode();
                    }
                };
                Future<String> a = pool.submit(task);
                Future<String> b = pool.submit(task);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();

                assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                        .as("%d번째 라운드", round).containsExactlyInAnyOrder("접수", "409");
                assertThat(reports.count()).as("%d번째 라운드", round).isEqualTo(round + 1);
                assertThat(sanctionNotifications(senior)).as("%d번째 라운드", round).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // 다른 트랜잭션이 어르신 행을 쥐고 있으면 신고는 풀릴 때까지 기다린다. 잠금이 없으면 바로 끝나 실패한다.
    @Test
    void 다른_트랜잭션이_어르신_행을_잠그고_있으면_신고는_풀릴_때까지_기다린다() throws Exception {
        User senior = newUser(Role.SENIOR, "잠금선배");
        Target target = newTarget(senior);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                seniorLock.lock(senior.getId());
                holderSessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Object> blocked = pool.submit(
                    () -> replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.ABUSE));
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000))
                    .as("잠금을 쥔 세션(%d)에 막힌 세션", holderSessionId.get()).isTrue();
            assertThat(blocked.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            blocked.get(30, TimeUnit.SECONDS);
            assertThat(replies.countVisibleBySeniorId(senior.getId())).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private boolean waitUntilBlockedBy(int holderSessionId, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?", Integer.class, holderSessionId);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}
