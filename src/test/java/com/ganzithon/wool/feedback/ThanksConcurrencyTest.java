package com.ganzithon.wool.feedback;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.feedback.request.CreateThanksRequest;
import com.ganzithon.wool.feedback.service.ThanksService;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.service.ReplyDeleteService;
import com.ganzithon.wool.report.entity.ReplyReportReason;
import com.ganzithon.wool.report.service.ReplyReportService;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.service.WorryDeleteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ThanksConcurrencyTest extends IntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired ReplyRepository replies;
    @Autowired ThanksRepository thanks;
    @Autowired NotificationRepository notifications;
    @Autowired ThanksService service;
    @Autowired ReplyDeleteService replyDelete;
    @Autowired ReplyReportService report;
    @Autowired WorryDeleteService worryDelete;
    @Autowired DatabaseCleaner cleaner;

    @AfterEach void clean() { cleaner.clean(); }

    @Test void 같은_답장에_동시에_보내면_한번만_저장한다() throws Exception {
        var pair = fixture();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return send(pair); });
            var b = pool.submit(() -> { start.await(); return send(pair); });
            start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "THANKS_409_001");
            assertThat(thanks.count()).isEqualTo(1);
            assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(pair.senior().getId())).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void 실제_삭제와_신고_요청이_겹쳐도_처리_순서에_맞게_저장한다() throws Exception {
        for (String change : List.of("replyDelete", "report", "worryDelete")) {
            for (int i = 0; i < 4; i++) {
                var pair = fixture();
                var pool = Executors.newFixedThreadPool(2);
                var start = new CountDownLatch(1);
                try {
                    var changed = pool.submit(() -> {
                        start.await();
                        switch (change) {
                            case "replyDelete" -> replyDelete.delete(pair.senior(), pair.reply().getId());
                            case "report" -> report.report(pair.youth(), pair.reply().getId(), ReplyReportReason.AD);
                            case "worryDelete" -> worryDelete.delete(pair.youth().getId(), pair.worry().getId());
                            default -> throw new IllegalStateException(change);
                        }
                        return true;
                    });
                    var sent = pool.submit(() -> { start.await(); return send(pair); });
                    start.countDown();
                    assertThat(changed.get(30, TimeUnit.SECONDS)).isTrue();
                    String result = sent.get(30, TimeUnit.SECONDS);
                    assertThat(result).isIn("success", change.equals("report") ? "REPLY_403_001" : "REPLY_404_001");
                    assertThat(thanks.existsByReplyId(pair.reply().getId())).isEqualTo(result.equals("success"));
                    long arrivalCount = notifications.findByUserIdOrderByCreatedAtDescIdDesc(pair.senior().getId())
                            .stream().filter(n -> n.getType().name().equals("THANKS_ARRIVED")).count();
                    assertThat(arrivalCount).isEqualTo(result.equals("success") ? 1 : 0);
                } finally { pool.shutdownNow(); }
            }
        }
    }

    private String send(Fixture f) {
        try {
            service.create(f.youth().getId(), f.reply().getId(), new CreateThanksRequest(ThanksStamp.GRATEFUL, null));
            return "success";
        } catch (BusinessException e) {
            return e.getErrorCode().getCode();
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "청" + suffix));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "선" + suffix));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
        return new Fixture(youth, senior, worry, reply);
    }

    private record Fixture(User youth, User senior, Worry worry, Reply reply) { }
}
