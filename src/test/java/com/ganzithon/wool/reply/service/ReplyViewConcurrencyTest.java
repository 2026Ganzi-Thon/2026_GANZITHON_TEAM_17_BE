package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.entity.Thanks;
import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.response.ReplyDetailResponse;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 답장 상세 조회는 고민 → 답장 순서로 행을 잠근 뒤 최초 조회 여부와 보낸 고마움을 읽는다.
// 결정적으로 만들기 위해 별도 트랜잭션이 답장 행을 쥔 채로 두고, 조회들이 잠금에서 기다리는 것을 확인한 뒤 풀어 준다.
// 조회가 잠금을 안 쓰면 기다리지 않아서 "기다린다"는 확인이 실패하고, 잠금 뒤에 읽지 않으면 아래 단언이 실패한다.
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
class ReplyViewConcurrencyTest extends IntegrationTestSupport {

    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ThanksRepository thanksRepository;
    @Autowired private ReplyViewService replyViewService;
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

    // 답장 행을 잠근 채 기다리는 트랜잭션. insertThanks면 고마움을 넣은 뒤(아직 커밋 전) 기다린다.
    private Future<Object> holdReplyLock(ExecutorService pool, Long replyId, boolean insertThanks,
                                         CountDownLatch locked, CountDownLatch release, AtomicInteger sessionId) {
        return pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Reply row = replies.findByIdAndDeletedAtIsNull(replyId).orElseThrow();
            if (insertThanks) {
                thanksRepository.saveAndFlush(Thanks.of(row, ThanksStamp.GRATEFUL, "고마워요"));
            }
            sessionId.set(jdbc.queryForObject("SELECT SESSION_ID()", Integer.class));
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
    }

    @Test
    void 같은_답장을_동시에_처음_열어도_첫_조회는_한_번만_나온다() throws Exception {
        User youth = newUser(Role.YOUTH, "조회청년");
        User senior = newUser(Role.SENIOR, "조회선배");
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = holdReplyLock(pool, reply.getId(), false, locked, release, holderSessionId);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ReplyDetailResponse> first = pool.submit(() -> replyViewService.view(youth.getId(), reply.getId()));
            Future<ReplyDetailResponse> second = pool.submit(() -> replyViewService.view(youth.getId(), reply.getId()));
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000))
                    .as("잠금을 쥔 세션(%d)에 막힌 세션", holderSessionId.get()).isTrue();
            assertThat(first.isDone() || second.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            List<ReplyDetailResponse> results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(results).filteredOn(ReplyDetailResponse::isFirstView).hasSize(1);
            assertThat(replies.findById(reply.getId()).orElseThrow().getFirstViewedAt()).isNotNull();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // 조회가 잠금을 기다리는 사이 고마움이 커밋되면, 조회는 그 고마움을 보고 THANKS 버튼을 내보내지 않는다.
    @Test
    void 조회가_잠금을_기다리는_사이_고마움이_커밋되면_그_고마움이_응답에_담긴다() throws Exception {
        User youth = newUser(Role.YOUTH, "조회청년");
        User senior = newUser(Role.SENIOR, "조회선배");
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderSessionId = new AtomicInteger(-1);
        try {
            Future<Object> holder = holdReplyLock(pool, reply.getId(), true, locked, release, holderSessionId);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ReplyDetailResponse> viewing = pool.submit(() -> replyViewService.view(youth.getId(), reply.getId()));
            assertThat(waitUntilBlockedBy(holderSessionId.get(), 10_000)).isTrue();
            assertThat(viewing.isDone()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            ReplyDetailResponse result = viewing.get(30, TimeUnit.SECONDS);

            assertThat(result.thanks()).isNotNull();
            assertThat(result.availableActions()).isEmpty();
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
