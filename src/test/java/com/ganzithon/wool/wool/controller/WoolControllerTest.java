package com.ganzithon.wool.wool.controller;

import com.ganzithon.wool.IntegrationTestSupport;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// WOOL-001 털 기록 조회
@AutoConfigureMockMvc
@Transactional
class WoolControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;

    private User youth;
    private User senior;
    private User other;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private Reply seed(User by, boolean hidden, boolean deleted) {
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = Reply.text(worry, by, "답장");
        if (hidden) reply.hide();
        if (deleted) reply.delete();
        return replies.saveAndFlush(reply);
    }

    private void seed(User by, int validCount) {
        for (int i = 0; i < validCount; i++) {
            seed(by, false, false);
        }
    }

    private ResultActions record(User who) throws Exception {
        var request = get("/api/senior/wool");
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "선배");
        other = newUser(Role.SENIOR, "다른선배");
    }

    @Test
    void 래퍼로_단계와_남은_수와_진행률을_내려준다() throws Exception {
        seed(senior, 8);

        record(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("WOOL_200_1"))
                .andExpect(jsonPath("$.message").value("털 기록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.totalReplies").value(8))
                .andExpect(jsonPath("$.data.stage").value("SCARF"))
                .andExpect(jsonPath("$.data.sweaterCount").value(0))
                .andExpect(jsonPath("$.data.nextStage").value("SWEATER"))
                .andExpect(jsonPath("$.data.remainingToNext").value(12))
                .andExpect(jsonPath("$.data.progress").value(0.14));
    }

    @Test
    void 답장이_한_통도_없으면_단계는_null이고_첫_단계가_다음이다() throws Exception {
        record(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReplies").value(0))
                .andExpect(jsonPath("$.data.stage").value(nullValue()))
                .andExpect(jsonPath("$.data.sweaterCount").value(0))
                .andExpect(jsonPath("$.data.nextStage").value("BALL"))
                .andExpect(jsonPath("$.data.remainingToNext").value(1))
                .andExpect(jsonPath("$.data.progress").value(0.0));
    }

    @Test
    void 숨기거나_삭제한_답장과_다른_어르신의_답장은_세지_않는다() throws Exception {
        seed(senior, 3);
        seed(senior, true, false);
        seed(senior, false, true);
        seed(senior, true, true);
        seed(other, 10);

        record(senior)
                .andExpect(jsonPath("$.data.totalReplies").value(3))
                .andExpect(jsonPath("$.data.stage").value("BALL"))
                .andExpect(jsonPath("$.data.nextStage").value("SCARF"))
                .andExpect(jsonPath("$.data.remainingToNext").value(3));
    }

    @Test
    void 스무_통이_되면_스웨터를_한_벌_완성하고_다음_스웨터까지의_진행률을_준다() throws Exception {
        seed(senior, 25);

        record(senior)
                .andExpect(jsonPath("$.data.totalReplies").value(25))
                .andExpect(jsonPath("$.data.stage").value("SWEATER"))
                .andExpect(jsonPath("$.data.sweaterCount").value(1))
                .andExpect(jsonPath("$.data.nextStage").value("SWEATER"))
                .andExpect(jsonPath("$.data.remainingToNext").value(15))
                .andExpect(jsonPath("$.data.progress").value(0.25));
    }

    @Test
    void 답장을_삭제하면_누적_수와_단계가_바로_내려간다() throws Exception {
        seed(senior, 5);
        Reply sixth = seed(senior, false, false);
        record(senior).andExpect(jsonPath("$.data.stage").value("SCARF")).andExpect(jsonPath("$.data.totalReplies").value(6));

        mockMvc.perform(delete("/api/replies/{replyId}", sixth.getId()).header("X-User-Id", senior.getId()))
                .andExpect(status().isOk());

        record(senior)
                .andExpect(jsonPath("$.data.totalReplies").value(5))
                .andExpect(jsonPath("$.data.stage").value("BALL"));
    }

    @Test
    void 헤더가_없으면_401_청년이면_403이다() throws Exception {
        record(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        record(youth).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }
}
