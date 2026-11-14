package com.ganzithon.wool.report.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.report.entity.ReplyReportReason;
import com.ganzithon.wool.report.repository.ReplyReportRepository;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.reply.response.CreateReplyResponse;
import com.ganzithon.wool.reply.response.DeleteReplyResponse;
import com.ganzithon.wool.reply.service.ReplyCreateService;
import com.ganzithon.wool.reply.service.ReplyDeleteService;
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
import org.springframework.test.context.TestPropertySource;
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

// 운영 MySQL의 기본 격리 수준(REPEATABLE READ)에서도 누적 수를 바꾸는 서비스(답장 작성·삭제·신고)가 어긋나지 않는지 확인한다.
// 이 테스트는 연결의 기본 격리 수준을 REPEATABLE READ로 두고(별도 H2 DB), 서비스가 선언한 READ COMMITTED가 그것을 덮어써서
// "잠금을 기다린 뒤에도 트랜잭션 시작 시점의 오래된 수를 읽는" 문제를 막는지 본다.
// 신고 테스트는 두 신고가 잠금 전 조회를 마치고 어르신 행 잠금에서 함께 기다리게 만든 뒤 풀어 주므로 순서가 매번 같다.
// 서비스의 @Transactional(isolation = READ_COMMITTED)를 빼면 아래 테스트가 실패한다.
// H2의 REPEATABLE READ는 MySQL보다 스냅샷을 더 일찍 만들어서, 이 테스트는 선언이 지워지는 것을 막는 안전망이다.
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:test_repeatable_read;MODE=MySQL",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
})
class RepeatableReadConcurrencyTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ReplyReportRepository reports;
    @Autowired private CertificateRepository certificates;
    @Autowired private NotificationRepository notifications;
    @Autowired private ReplyReportService replyReportService;
    @Autowired private ReplyCreateService replyCreateService;
    @Autowired private ReplyDeleteService replyDeleteService;
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

    private Worry newWorry(User author) {
        return worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private record Target(User youth, Reply reply) {
    }

    private Target newTarget(User senior) {
        User youth = newUser(Role.YOUTH, "격리청년");
        return new Target(youth, replies.saveAndFlush(Reply.text(newWorry(youth), senior, "답장")));
    }

    private List<Notification> sanctionNotifications(User senior) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).stream()
                .filter(n -> n.getType() == NotificationType.SANCTION).toList();
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

    // 어르신에 대한 신고자가 existing명인 상태에서 서로 다른 두 청년이 동시에 신고한다. 마지막 신고가 제재 기준을 넘는다.
    private void reportTwoAtOnce(User senior, int existing, SanctionLevel expectedLevel, int expectedNotifications) throws Exception {
        for (int i = 0; i < existing; i++) {
            Target target = newTarget(senior);
            replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.ABUSE);
        }
        Target first = newTarget(senior);
        Target second = newTarget(senior);

        // 결정적으로 만든다: 별도 트랜잭션이 어르신 행을 쥔 채로 두 신고를 시작해, 두 신고가 모두 잠금 앞에서
        // (잠금 전 일반 조회로 스냅샷을 만든 뒤) 기다리는 것을 확인하고 나서 풀어 준다.
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

            List<Future<String>> results = new ArrayList<>();
            for (Target target : List.of(first, second)) {
                Callable<String> task = () -> {
                    replyReportService.report(target.youth(), target.reply().getId(), ReplyReportReason.AD);
                    return "ok";
                };
                results.add(pool.submit(task));
            }
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 2, 15_000))
                    .as("두 신고가 모두 잠금을 쥔 세션(%d)에 막혀 있어야 한다", holderSessionId.get()).isTrue();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            for (Future<String> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("ok");
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(existing + 2);
        assertThat(users.findById(senior.getId()).orElseThrow().getSanctionLevel()).isEqualTo(expectedLevel);
        assertThat(sanctionNotifications(senior)).hasSize(expectedNotifications);
    }

    // 신고자 1명(경고) → 동시에 2명 더: 3명째가 7일 제한이다. 오래된 수를 읽으면 제한이 빠진다.
    @Test
    void 동시에_신고해_세_번째_기준을_넘어도_7일_제한이_빠지지_않는다() throws Exception {
        for (int round = 0; round < 8; round++) {
            User senior = newUser(Role.SENIOR, "격리선배");
            reportTwoAtOnce(senior, 1, SanctionLevel.RESTRICTED, 2);
        }
    }

    // 신고자 3명(7일 제한) → 동시에 2명 더: 5명째가 영구 제한이다.
    @Test
    void 동시에_신고해_다섯_번째_기준을_넘어도_영구_제한이_빠지지_않는다() throws Exception {
        for (int round = 0; round < 6; round++) {
            User senior = newUser(Role.SENIOR, "격리선배");
            reportTwoAtOnce(senior, 3, SanctionLevel.BANNED, 3);
        }
    }

    // 19통인 어르신이 서로 다른 고민에 동시에 답장하면 20통째 감사장이 한 장만 발급된다.
    @Test
    void 동시에_답장해도_스무_번째_감사장은_한_장만_발급된다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                User senior = newUser(Role.SENIOR, "격리선배");
                for (int i = 0; i < 19; i++) {
                    newTarget(senior);
                }
                Long worry1 = newWorry(newUser(Role.YOUTH, "격리청년")).getId();
                Long worry2 = newWorry(newUser(Role.YOUTH, "격리청년")).getId();

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<CreateReplyResponse>> results = new ArrayList<>();
                for (Long worryId : List.of(worry1, worry2)) {
                    Callable<CreateReplyResponse> task = () -> {
                        ready.countDown();
                        go.await();
                        return replyCreateService.create(senior, worryId, new CreateReplyRequest(ReplyType.TEXT, "답장", null));
                    };
                    results.add(pool.submit(task));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                List<Integer> totals = new ArrayList<>();
                for (Future<CreateReplyResponse> result : results) {
                    totals.add((int) result.get(30, TimeUnit.SECONDS).totalReplies());
                }

                assertThat(totals).as("%d번째 라운드", round).containsExactlyInAnyOrder(20, 21);
                assertThat(certificates.findBySeniorIdOrderByIssuedAtDescIdDesc(senior.getId()))
                        .as("%d번째 라운드", round).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // 19통인 어르신이 한 통을 지우는 것과 새 답장을 쓰는 것이 겹쳐도 한 순서로 맞아떨어진다.
    @Test
    void 동시에_삭제와_작성이_겹쳐도_누적_수와_감사장이_어느_한_순서로_맞아떨어진다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                User senior = newUser(Role.SENIOR, "격리선배");
                Long deleteId = null;
                for (int i = 0; i < 19; i++) {
                    Target target = newTarget(senior);
                    if (deleteId == null) deleteId = target.reply().getId();
                }
                Long targetReplyId = deleteId;
                Long newWorryId = newWorry(newUser(Role.YOUTH, "격리청년")).getId();

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Callable<CreateReplyResponse> createTask = () -> {
                    ready.countDown();
                    go.await();
                    return replyCreateService.create(senior, newWorryId, new CreateReplyRequest(ReplyType.TEXT, "답장", null));
                };
                Callable<DeleteReplyResponse> deleteTask = () -> {
                    ready.countDown();
                    go.await();
                    return replyDeleteService.delete(senior, targetReplyId);
                };
                Future<CreateReplyResponse> created = pool.submit(createTask);
                Future<DeleteReplyResponse> deleted = pool.submit(deleteTask);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                CreateReplyResponse createResult = created.get(30, TimeUnit.SECONDS);
                DeleteReplyResponse deleteResult = deleted.get(30, TimeUnit.SECONDS);

                boolean createFirst = createResult.totalReplies() == 20;
                if (createFirst) {
                    assertThat(createResult.certificateIssued()).as("%d번째 라운드", round).isTrue();
                    assertThat(deleteResult.totalReplies()).as("%d번째 라운드", round).isEqualTo(19);
                } else {
                    assertThat(createResult.totalReplies()).as("%d번째 라운드", round).isEqualTo(19);
                    assertThat(createResult.certificateIssued()).as("%d번째 라운드", round).isFalse();
                    assertThat(deleteResult.totalReplies()).as("%d번째 라운드", round).isEqualTo(18);
                }
                assertThat(replies.countVisibleBySeniorId(senior.getId())).as("%d번째 라운드", round).isEqualTo(19);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
