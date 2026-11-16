package com.ganzithon.wool.feedback;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.entity.FollowUp;
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.feedback.service.FollowUpService;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class FollowUpVisibilityConcurrencyTest extends IntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired ReplyRepository replies;
    @Autowired FollowUpRepository followUps;
    @Autowired FollowUpService service;
    @Autowired com.ganzithon.wool.worry.service.WorryVisibilityService worryVisibility;
    @Autowired com.ganzithon.wool.reply.service.ReplyDeleteService replyDelete;
    @Autowired com.ganzithon.wool.report.service.ReplyReportService report;
    @Autowired DatabaseCleaner cleaner;
    @AfterEach void clean() { cleaner.clean(); }
    @Test void 같은_후일담을_동시에_변경하면_한번만_성공한다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "동시청년"));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "동시선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
        followUps.saveAndFlush(FollowUp.of(reply, "후일담", WorryVisibility.PUBLIC));
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> task = () -> { start.await(); try { service.changeVisibility(youth.getId(), reply.getId(), WorryVisibility.SENIORS_ONLY); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder("success", "FOLLOWUP_409_002");
        } finally { pool.shutdownNow(); }
    }
    @Test void 고민_비공개와_답장_삭제_신고가_후일담_변경과_겹쳐도_순서에_맞다() throws Exception {
        for (String action : List.of("worry", "delete", "report")) {
            for (int n = 0; n < 5; n++) {
                String suffix = java.util.UUID.randomUUID().toString().substring(0, 6);
                User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "청" + suffix));
                User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "선" + suffix));
                Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
                Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
                followUps.saveAndFlush(FollowUp.of(reply, "후일담", WorryVisibility.PUBLIC));
                var pool = Executors.newFixedThreadPool(2);
                var start = new CountDownLatch(1);
                try {
                    var first = pool.submit(() -> { start.await(); try { service.changeVisibility(youth.getId(), reply.getId(), WorryVisibility.SENIORS_ONLY); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } });
                    var second = pool.submit(() -> { start.await(); if (action.equals("worry")) worryVisibility.change(youth.getId(), worry.getId(), WorryVisibility.SENIORS_ONLY); else if (action.equals("delete")) replyDelete.delete(senior, reply.getId()); else report.report(youth, reply.getId(), com.ganzithon.wool.report.entity.ReplyReportReason.AD); return true; });
                    start.countDown();
                    assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
                    assertThat(first.get(30, TimeUnit.SECONDS)).isIn("success", action.equals("worry") ? "FOLLOWUP_409_002" : action.equals("delete") ? "FOLLOWUP_404_001" : "REPLY_403_001");
                } finally { pool.shutdownNow(); }
            }
        }
    }

}
