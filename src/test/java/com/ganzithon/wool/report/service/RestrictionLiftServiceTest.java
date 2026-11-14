package com.ganzithon.wool.report.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.report.entity.ReplyReportReason;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 7일 답장 제한 자동 해제. (POL-RPT-06) 공유 DB를 쓰는 동시성 테스트가 있어서 트랜잭션 롤백 대신 DatabaseCleaner로 비운다.
class RestrictionLiftServiceTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ReplyReportRepository reports;
    @Autowired private RestrictionLiftService restrictionLiftService;
    @Autowired private RestrictionLifter restrictionLifter;
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

    private User seniorWith(SanctionLevel level, LocalDateTime restrictedUntil) {
        User senior = newUser(Role.SENIOR, "해제선배");
        senior.applySanction(level, restrictedUntil);
        return users.saveAndFlush(senior);
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private LocalDateTime now() {
        // DB 시각 정밀도(마이크로초)와 어긋나 경계값 비교가 흔들리지 않게 초 단위로 맞춘다.
        return LocalDateTime.now().withNano(0);
    }

    @Test
    void 기간이_지난_7일_제한은_풀리고_기간이_남은_제한은_그대로다() {
        LocalDateTime now = now();
        User expired = seniorWith(SanctionLevel.RESTRICTED, now.minusMinutes(1));
        User exactly = seniorWith(SanctionLevel.RESTRICTED, now);
        User remaining = seniorWith(SanctionLevel.RESTRICTED, now.plusMinutes(1));

        int lifted = restrictionLiftService.liftExpired(now);

        assertThat(lifted).isEqualTo(2);
        assertThat(reload(expired).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(reload(expired).getRestrictedUntil()).isNull();
        assertThat(reload(exactly).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(reload(remaining).getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
        assertThat(reload(remaining).getRestrictedUntil()).isNotNull();
    }

    // 후보 목록과 별개로, 풀기 직전에 잠금을 잡고 만료 시각을 다시 확인한다.
    @Test
    void 기간이_남은_어르신을_직접_풀려고_해도_풀리지_않는다() {
        LocalDateTime now = now();
        User remaining = seniorWith(SanctionLevel.RESTRICTED, now.plusMinutes(1));

        assertThat(restrictionLifter.liftIfExpired(remaining.getId(), now)).isFalse();
        assertThat(reload(remaining).getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
        assertThat(restrictionLifter.liftIfExpired(remaining.getId(), now.plusMinutes(1))).isTrue();
    }

    @Test
    void 경고_영구_제한_제재없음은_건드리지_않는다() {
        LocalDateTime now = now();
        User warned = seniorWith(SanctionLevel.WARNED, null);
        User banned = seniorWith(SanctionLevel.BANNED, null);
        User none = seniorWith(SanctionLevel.NONE, null);
        // 영구 제한인데 예전 만료 시각이 남아 있어도 풀지 않는다.
        User bannedWithOldDate = seniorWith(SanctionLevel.BANNED, now.minusDays(1));

        assertThat(restrictionLiftService.liftExpired(now)).isZero();

        assertThat(reload(warned).getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
        assertThat(reload(banned).getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
        assertThat(reload(none).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(reload(bannedWithOldDate).getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
    }

    @Test
    void 두_번_실행해도_한_번만_풀리고_해제_뒤에도_누적_신고_수는_그대로다() {
        User senior = newUser(Role.SENIOR, "해제선배");
        for (int i = 0; i < 3; i++) {
            User youth = newUser(Role.YOUTH, "해제청년");
            Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
            Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
            replyReportService.report(youth, reply.getId(), ReplyReportReason.ABUSE);
        }
        assertThat(reload(senior).getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
        LocalDateTime afterExpiry = now().plusDays(8);

        assertThat(restrictionLiftService.liftExpired(afterExpiry)).isEqualTo(1);
        assertThat(restrictionLiftService.liftExpired(afterExpiry)).isZero();

        assertThat(reload(senior).getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(3);
        assertThat(reload(senior).isReplyRestrictedAt(afterExpiry)).isFalse();
    }

    // 후보를 100명씩 끊어 읽지만 한 번의 실행에서 끝까지 이어서 처리한다.
    @Test
    void 백_명이_넘어도_한_번의_실행에서_모두_푼다() {
        LocalDateTime now = now();
        for (int i = 0; i < RestrictionLiftService.BATCH_SIZE + 5; i++) {
            seniorWith(SanctionLevel.RESTRICTED, now.minusMinutes(1));
        }

        assertThat(restrictionLiftService.liftExpired(now)).isEqualTo(RestrictionLiftService.BATCH_SIZE + 5);
        assertThat(restrictionLiftService.liftExpired(now)).isZero();
    }

    // 5번째 신고와 해제가 겹쳐도 어느 순서든 최종은 영구 제한이다. (실제 신고 서비스와의 조합)
    // 별도 트랜잭션이 어르신 행을 쥔 채 신고와 해제를 함께 시작하고, 둘 다 기다리는 것을 확인한 뒤 풀어 준다.
    @Test
    void 다섯_번째_신고와_해제가_겹쳐도_최종은_영구_제한이다() throws Exception {
        for (int round = 0; round < 6; round++) {
            User senior = newUser(Role.SENIOR, "경쟁선배");
            for (int i = 0; i < 4; i++) {
                User youth = newUser(Role.YOUTH, "경쟁청년");
                Reply reply = replies.saveAndFlush(Reply.text(
                        worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC)), senior, "답장"));
                replyReportService.report(youth, reply.getId(), ReplyReportReason.ABUSE);
            }
            // 4명째까지 신고돼 RESTRICTED이고, 7일이 이미 지난 상태로 만든다.
            User stored = reload(senior);
            stored.applySanction(SanctionLevel.RESTRICTED, now().minusMinutes(1));
            users.saveAndFlush(stored);
            User fifth = newUser(Role.YOUTH, "경쟁청년");
            Reply fifthReply = replies.saveAndFlush(Reply.text(
                    worries.saveAndFlush(Worry.of(fifth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC)), senior, "답장"));

            ExecutorService pool = Executors.newFixedThreadPool(3);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger holderSessionId = new AtomicInteger(-1);
            try {
                Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    seniorLock.lock(senior.getId());
                    holderSessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
                    locked.countDown();
                    try {
                        release.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

                LocalDateTime now = now();
                Future<Object> reporting = pool.submit(
                        () -> replyReportService.report(fifth, fifthReply.getId(), ReplyReportReason.AD));
                Future<Integer> lifting = pool.submit(() -> restrictionLiftService.liftExpired(now));
                assertThat(waitUntilBlockedBy(holderSessionId.get(), 2, 15_000)).isTrue();

                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                reporting.get(30, TimeUnit.SECONDS);
                lifting.get(30, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                pool.shutdownNow();
            }

            assertThat(reload(senior).getSanctionLevel()).as("%d번째 라운드", round).isEqualTo(SanctionLevel.BANNED);
            assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).as("%d번째 라운드", round).isEqualTo(5);
        }
    }

    // 후보로 뽑힌 뒤 풀기 전에 다른 트랜잭션이 영구 제한으로 올리면 풀지 않는다. (잠금 뒤 재확인)
    // 별도 트랜잭션이 어르신 행을 쥔 채 해제를 시작하고, 해제가 잠금에서 기다리는 것을 확인한 뒤 영구 제한을 커밋한다.
    @Test
    void 해제가_잠금을_기다리는_사이_영구_제한이_되면_풀지_않는다() throws Exception {
        LocalDateTime now = now();
        User senior = seniorWith(SanctionLevel.RESTRICTED, now.minusMinutes(1));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                User locked1 = seniorLock.lock(senior.getId());
                holderSessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // 영구 제한이어도 예전 만료 시각이 남아 있는 경우: 단계 확인이 유일한 방어다.
                locked1.applySanction(SanctionLevel.BANNED, now.minusMinutes(1));
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> lifting = pool.submit(() -> restrictionLiftService.liftExpired(now));
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 1, 10_000)).isTrue();
            assertThat(lifting.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(lifting.get(30, TimeUnit.SECONDS)).isZero();
            assertThat(reload(senior).getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private boolean waitUntilBlockedBy(int holderSessionId, int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?", Integer.class, holderSessionId);
            if (waiting != null && waiting >= expected) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}
