package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.reply.response.CreateReplyResponse;
import com.ganzithon.wool.reply.response.DeleteReplyResponse;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
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

// 답장 삭제는 답장 행을 잠근 뒤 판단하고, 누적 수를 바꾸는 작업이라 어르신 행도 답장 작성과 같은 방식으로 잠근다.
// 수정, 고마움, 신고도 같은 답장 행 잠금을 쓰게 될 것이라 먼저 확인해 둔다.
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
class ReplyDeleteConcurrencyTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private CertificateRepository certificates;
    @Autowired private ReplyDeleteService replyDeleteService;
    @Autowired private ReplyCreateService replyCreateService;
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

    private User newSenior() {
        return users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, name("삭선")));
    }

    private Worry newWorry() {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, name("삭청")));
        return worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private Reply newReply(User senior) {
        return replies.saveAndFlush(Reply.text(newWorry(), senior, "답장"));
    }

    // 같은 답장을 동시에 삭제하면 한 번만 성공하고 나머지는 404다. 누적 수가 두 번 줄어들지 않는다.
    @Test
    void 같은_답장을_동시에_삭제하면_한_번만_성공하고_나머지는_404다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                User senior = newSenior();
                Long replyId = newReply(senior).getId();
                newReply(senior); // 누적 수가 0으로 내려가지 않게 하나 더 둔다.

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Callable<String> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return "성공 " + replyDeleteService.delete(senior, replyId).totalReplies();
                    } catch (BusinessException e) {
                        return e.getErrorCode() == ReplyErrorCode.NOT_FOUND ? "404" : "다른 오류: " + e.getErrorCode();
                    }
                };
                Future<String> first = pool.submit(task);
                Future<String> second = pool.submit(task);
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();

                assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                        .as("%d번째 라운드", round).containsExactlyInAnyOrder("성공 1", "404");
                assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // 답장 작성과 삭제가 같은 어르신 기준으로 한 번에 하나씩 처리되는지 확인한다. 유효 답장이 19통인 어르신이 한 통을 지우는 것과
    // 새 답장을 쓰는 것이 겹치면, 가능한 결과는 둘뿐이다.
    //   작성이 먼저: 작성 20통(감사장 발급), 삭제 19통
    //   삭제가 먼저: 삭제 18통, 작성 19통(감사장 없음)
    // 잠금을 공유하지 않으면 "작성 20통(감사장 발급), 삭제 18통"처럼 어느 순서로도 설명되지 않는 결과가 나올 수 있다.
    @Test
    void 답장_작성과_삭제가_동시에_일어나도_누적_수와_감사장이_어느_한_순서로_맞아떨어진다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                User senior = newSenior();
                Long targetId = null;
                for (int i = 0; i < 19; i++) {
                    Reply seeded = newReply(senior);
                    if (targetId == null) targetId = seeded.getId();
                }
                Long deleteId = targetId;
                Long newWorryId = newWorry().getId();

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
                    return replyDeleteService.delete(senior, deleteId);
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
                assertThat(certificates.existsBySeniorIdAndReplyCount(senior.getId(), 20))
                        .as("%d번째 라운드", round).isEqualTo(createFirst);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // 아래 두 테스트는 한 트랜잭션이 잠금을 쥔 채 멈춰 있게 만들고, 삭제가 그 트랜잭션 때문에 기다리는지 결정적으로 확인한다.
    // 잠금이 없으면 삭제가 바로 끝나 실패한다.
    @Test
    void 다른_트랜잭션이_답장_행을_잠그고_있으면_삭제는_풀릴_때까지_기다린다() throws Exception {
        User senior = newSenior();
        Long replyId = newReply(senior).getId();

        assertWaitsWhileLocked(
                () -> replies.findByIdAndDeletedAtIsNull(replyId),
                () -> replyDeleteService.delete(senior, replyId));
        assertThat(replies.countVisibleBySeniorId(senior.getId())).isZero();
    }

    @Test
    void 다른_트랜잭션이_어르신_행을_잠그고_있으면_삭제는_풀릴_때까지_기다린다() throws Exception {
        User senior = newSenior();
        Long replyId = newReply(senior).getId();

        assertWaitsWhileLocked(
                () -> seniorLock.lock(senior.getId()),
                () -> replyDeleteService.delete(senior, replyId));
        assertThat(replies.countVisibleBySeniorId(senior.getId())).isZero();
    }

    // lockAction으로 행을 잠근 트랜잭션을 열어 둔 채 waiting을 다른 스레드에서 시작하고, "잠금을 쥔 그 세션"에 막힌 세션이
    // 생길 때까지 기다린다. 잠금이 풀리기 전에는 waiting이 끝나지 않다가 풀린 뒤에는 성공하는지 확인한다.
    private void assertWaitsWhileLocked(Runnable lockAction, Callable<Object> waiting) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                lockAction.run();
                // 같은 트랜잭션의 연결로 H2 세션 번호를 읽는다.
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

            Future<Object> blocked = pool.submit(waiting);
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000))
                    .as("잠금을 쥔 세션(%d)에 막힌 세션", holderSessionId.get()).isTrue();
            assertThat(blocked.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            blocked.get(30, TimeUnit.SECONDS);
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
