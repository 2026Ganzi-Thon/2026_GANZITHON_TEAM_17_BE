package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.entity.Thanks;
import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 답장 수정은 답장 행을 잠근 뒤 고마움·후일담이 있는지 판단한다. 고마움이 그 사이에 커밋되면 수정은 409가 되어야 한다.
// 결정적으로 만들기 위해 별도 트랜잭션이 답장 행을 쥔 채 고마움을 넣고, 수정이 잠금에서 기다리는 것을 확인한 뒤 커밋한다.
// 수정이 잠금을 안 쓰면 바로 끝나서 "기다린다"는 확인이 실패하고, 잠금 뒤 확인이 없으면 수정이 성공해서 실패한다.
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
class ReplyUpdateConcurrencyTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ThanksRepository thanksRepository;
    @Autowired private ReplyUpdateService replyUpdateService;
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

    @Test
    void 수정이_잠금을_기다리는_사이_고마움이_커밋되면_수정은_409이고_내용은_그대로다() throws Exception {
        User youth = newUser(Role.YOUTH, "수정청년");
        User senior = newUser(Role.SENIOR, "수정선배");
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "처음 답장"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                // 고마움을 보내는 쪽이 하는 것과 같이 답장 행을 잠그고 고마움을 넣는다. (아직 커밋 전)
                Reply locked1 = replies.findByIdAndDeletedAtIsNull(reply.getId()).orElseThrow();
                thanksRepository.saveAndFlush(Thanks.of(locked1, ThanksStamp.GRATEFUL, "고마워요"));
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

            Future<Object> updating = pool.submit(() -> replyUpdateService.update(senior, reply.getId(), "고친 답장"));
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000))
                    .as("잠금을 쥔 세션(%d)에 막힌 세션", holderSessionId.get()).isTrue();
            assertThat(updating.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);

            assertThatThrownByUpdate(updating);
            assertThat(replies.findById(reply.getId()).orElseThrow().getContent()).isEqualTo("처음 답장");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // 고마움이 없을 때는 잠금을 기다렸다가 그대로 수정된다.
    @Test
    void 다른_트랜잭션이_답장_행을_잠그고_있으면_수정은_풀릴_때까지_기다렸다가_수정된다() throws Exception {
        User youth = newUser(Role.YOUTH, "수정청년");
        User senior = newUser(Role.SENIOR, "수정선배");
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "처음 답장"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                replies.findByIdAndDeletedAtIsNull(reply.getId()).orElseThrow();
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

            Future<Object> updating = pool.submit(() -> replyUpdateService.update(senior, reply.getId(), "고친 답장"));
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000)).isTrue();
            assertThat(updating.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            updating.get(30, TimeUnit.SECONDS);

            assertThat(replies.findById(reply.getId()).orElseThrow().getContent()).isEqualTo("고친 답장");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void assertThatThrownByUpdate(Future<Object> updating) throws Exception {
        try {
            updating.get(30, TimeUnit.SECONDS);
            throw new AssertionError("고마움을 받은 답장의 수정이 성공했습니다.");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) e.getCause()).getErrorCode()).isEqualTo(ReplyErrorCode.FEEDBACK_RECEIVED);
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
