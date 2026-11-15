package com.ganzithon.wool.feedback;

import com.ganzithon.wool.DatabaseCleaner;
import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.feedback.request.CreateFollowUpRequest;
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
import com.ganzithon.wool.worry.service.WorryVisibilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class FollowUpConcurrencyTest extends IntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired ReplyRepository replies;
    @Autowired FollowUpRepository followUps;
    @Autowired FollowUpService service;
    @Autowired WorryVisibilityService visibility;
    @Autowired DatabaseCleaner cleaner;
    @Autowired com.ganzithon.wool.reply.service.ReplyDeleteService deleteService;
    @Autowired com.ganzithon.wool.report.service.ReplyReportService reportService;
    @AfterEach void clean() { cleaner.clean(); }
    @Test void 공개범위_변경과_후일담_작성은_비공개_규칙을_유지한다() throws Exception {
        for (int i = 0; i < 5; i++) {
            User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "경쟁청년" + i));
            User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "경쟁선배" + i));
            Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
            Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
            var pool = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                var first = pool.submit(() -> { start.await(); try { service.create(youth.getId(), reply.getId(), new CreateFollowUpRequest("감사합니다", WorryVisibility.PUBLIC)); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } });
                var second = pool.submit(() -> { start.await(); visibility.change(youth.getId(), worry.getId(), WorryVisibility.SENIORS_ONLY); return true; });
                start.countDown();
                String result = first.get(30, TimeUnit.SECONDS);
                assertThat(result).isIn("success", "FOLLOWUP_400_001");
                assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
                var saved = followUps.findAll().stream().filter(f -> f.getReply().getId().equals(reply.getId())).toList();
                assertThat(saved).hasSize(result.equals("success") ? 1 : 0);
                if (!saved.isEmpty()) assertThat(saved.get(0).getVisibility()).isEqualTo(WorryVisibility.SENIORS_ONLY);
            } finally { pool.shutdownNow(); }
        }
    }
    @Test void 답장_삭제와_신고가_후일담_작성과_겹쳐도_순서에_맞게_처리한다() throws Exception {
        for (String action : java.util.List.of("delete", "report")) {
            for (int i = 0; i < 5; i++) {
                String suffix = java.util.UUID.randomUUID().toString().substring(0, 5);
                User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "청" + suffix));
                User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "선" + suffix));
                Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
                Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
                var pool = Executors.newFixedThreadPool(2);
                var start = new CountDownLatch(1);
                try {
                    var write = pool.submit(() -> { start.await(); try { service.create(youth.getId(), reply.getId(), new CreateFollowUpRequest("감사합니다", null)); return "success"; } catch (BusinessException e) { return e.getErrorCode().getCode(); } });
                    var change = pool.submit(() -> { start.await(); if (action.equals("delete")) deleteService.delete(senior, reply.getId()); else reportService.report(youth, reply.getId(), com.ganzithon.wool.report.entity.ReplyReportReason.AD); return true; });
                    start.countDown();
                    assertThat(change.get(30, TimeUnit.SECONDS)).isTrue();
                    String result = write.get(30, TimeUnit.SECONDS);
                    assertThat(result).isIn("success", action.equals("delete") ? "REPLY_404_001" : "REPLY_403_001");
                    assertThat(followUps.existsByReplyId(reply.getId())).isEqualTo(result.equals("success"));
                } finally { pool.shutdownNow(); }
            }
        }
    }

}
