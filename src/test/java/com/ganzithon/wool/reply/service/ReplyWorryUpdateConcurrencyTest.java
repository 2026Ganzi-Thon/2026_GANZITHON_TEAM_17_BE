package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.request.UpdateWorryRequest;
import com.ganzithon.wool.worry.service.WorryUpdateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
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

// 같은 고민에 대한 "고민 수정"과 "답장 등록"이 동시에 들어오는 상황을 실제 스레드 두 개로 실행한다.
// 둘 다 고민 행의 쓰기 잠금을 쓰므로 한쪽이 끝난 뒤에 다른 쪽이 실행된다. 가능한 결과는 둘뿐이다.
//   1) 수정이 먼저: 수정 성공, 그다음 답장 성공. 수정 시각이 답장 시각보다 늦지 않다.
//   2) 답장이 먼저: 답장 성공, 그다음 수정은 409이고 제목은 그대로다.
// 잠금이 없으면 "답장이 먼저 저장됐는데 수정도 성공"하는 어긋난 결과가 나올 수 있다.
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
class ReplyWorryUpdateConcurrencyTest extends IntegrationTestSupport {

    private static final int ROUNDS = 25;

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ReplyCreateService replyCreateService;
    @Autowired private WorryUpdateService worryUpdateService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanUp() {
        databaseCleaner.clean();
    }

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void 수정과_답장이_동시에_들어와도_답장이_달린_고민은_수정되지_않는다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, name("동청")));
                User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, name("동선")));
                Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
                Long worryId = worry.getId();

                UpdateWorryRequest update = new UpdateWorryRequest();
                update.setTitle("새 제목");
                CreateReplyRequest reply = new CreateReplyRequest(ReplyType.TEXT, "답장", null);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Callable<String> updateTask = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        worryUpdateService.update(youth.getId(), worryId, update);
                        return "수정 성공";
                    } catch (BusinessException e) {
                        return e.getErrorCode() == WorryErrorCode.UPDATE_CONFLICT ? "수정 409" : "다른 오류: " + e.getErrorCode();
                    }
                };
                Callable<String> replyTask = () -> {
                    ready.countDown();
                    go.await();
                    replyCreateService.create(senior, worryId, reply);
                    return "답장 성공";
                };
                Future<String> updateResult = pool.submit(updateTask);
                Future<String> replyResult = pool.submit(replyTask);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();

                String updateOutcome = updateResult.get(30, TimeUnit.SECONDS);
                String replyOutcome = replyResult.get(30, TimeUnit.SECONDS);

                assertThat(replyOutcome).as("%d번째 라운드", round).isEqualTo("답장 성공");
                Worry after = worries.findById(worryId).orElseThrow();
                LocalDateTime replyCreatedAt = new TransactionTemplate(transactionManager).execute(status ->
                        replies.findAll().stream().filter(r -> r.getWorry().getId().equals(worryId))
                                .findFirst().orElseThrow().getCreatedAt());
                if (updateOutcome.equals("수정 성공")) {
                    assertThat(after.getTitle()).as("%d번째 라운드", round).isEqualTo("새 제목");
                    assertThat(after.getUpdatedAt()).as("%d번째 라운드: 수정이 답장보다 먼저 끝나야 한다", round)
                            .isBeforeOrEqualTo(replyCreatedAt);
                } else {
                    assertThat(updateOutcome).as("%d번째 라운드", round).isEqualTo("수정 409");
                    assertThat(after.getTitle()).as("%d번째 라운드", round).isEqualTo("제목");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // 위 테스트는 실제로 겹쳐 실행하지만 어느 쪽이 먼저 가는지는 스케줄링에 달려 있다.
    // 아래 두 테스트는 한 트랜잭션이 잠금을 쥔 채로 멈춰 있게 만들어, 답장이 "기다리는지"를 순서와 상관없이 결정적으로 확인한다.
    // 잠금이 없으면 답장이 바로 끝나서 실패한다.
    @Test
    void 다른_트랜잭션이_고민_행을_잠그고_있으면_답장은_풀릴_때까지_기다린다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, name("잠청")));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, name("잠선")));
        Long worryId = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC)).getId();

        assertWaitsWhileLocked(
                () -> worries.findByIdAndDeletedAtIsNull(worryId),
                () -> replyCreateService.create(senior, worryId, new CreateReplyRequest(ReplyType.TEXT, "답장", null)));
    }

    @Test
    void 다른_트랜잭션이_어르신_행을_잠그고_있으면_다른_고민의_답장도_풀릴_때까지_기다린다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, name("잠청")));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, name("잠선")));
        Long worryId = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC)).getId();

        assertWaitsWhileLocked(
                () -> {
                    User locked = entityManager.find(User.class, senior.getId());
                    entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
                },
                () -> replyCreateService.create(senior, worryId, new CreateReplyRequest(ReplyType.TEXT, "답장", null)));
    }

    // lockAction으로 행을 잠근 트랜잭션을 열어 둔 채 waiting을 다른 스레드에서 시작하고, 잠금이 풀리기 전에는 끝나지 않다가
    // 풀린 뒤에는 성공하는지 확인한다.
    private void assertWaitsWhileLocked(Runnable lockAction, Callable<Object> waiting) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                lockAction.run();
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Object> blocked = pool.submit(waiting);
            // 시간이 지났다는 것만으로는 스레드가 늦게 시작했을 가능성을 배제할 수 없어서, H2가 "다른 세션을 기다리는 세션"이 있다고
            // 알려 줄 때까지 기다린다. 잠금이 없으면 답장이 바로 끝나 이 상태가 오지 않아 실패한다.
            assertThat(waitUntilSomeSessionIsBlocked(10_000)).as("DB에서 잠금을 기다리는 세션").isTrue();
            assertThat(blocked.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            blocked.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private boolean waitUntilSomeSessionIsBlocked(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL", Integer.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    // 같은 어르신이 서로 다른 고민에 동시에 답장해도 누적 수 계산과 감사장 발급이 어긋나지 않아야 한다.
    @Test
    void 같은_어르신의_동시_답장에서도_스무_번째_답장은_감사장_한_장을_발급한다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, name("동청")));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, name("동선")));
        List<Long> worryIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            worryIds.add(worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC)).getId());
        }
        CreateReplyRequest reply = new CreateReplyRequest(ReplyType.TEXT, "답장", null);

        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (Long worryId : worryIds) {
                results.add(pool.submit(() -> {
                    go.await();
                    return replyCreateService.create(senior, worryId, reply).certificateIssued();
                }));
            }
            go.countDown();
            long issued = 0;
            for (Future<Boolean> result : results) {
                if (result.get(60, TimeUnit.SECONDS)) {
                    issued++;
                }
            }
            assertThat(issued).as("감사장을 발급받았다고 응답한 답장 수").isEqualTo(1);
            assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }
}
