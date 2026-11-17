package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.entity.FollowUp;
import com.ganzithon.wool.feedback.entity.Thanks;
import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REPLY-001 어르신 홈 요약
@AutoConfigureMockMvc
@Transactional
class SeniorHomeControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ThanksRepository thanksRepository;
    @Autowired private FollowUpRepository followUpRepository;
    @Autowired private JdbcTemplate jdbc;

    private User youth;
    private User senior;
    private User other;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private Worry newWorry(WorryVisibility visibility) {
        return worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, visibility));
    }

    private Worry newWorry() {
        return newWorry(WorryVisibility.PUBLIC);
    }

    private Reply replyOn(Worry worry, User by) {
        return replies.saveAndFlush(Reply.text(worry, by, "답장"));
    }

    private ResultActions home(User who) throws Exception {
        var request = get("/api/senior/home");
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    private long number(String field) throws Exception {
        String body = home(senior).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"" + field + "\":([0-9]+).*", "$1"));
    }

    private void createdAt(Worry worry, LocalDateTime at) {
        jdbc.update("UPDATE worries SET created_at = ? WHERE id = ?", at, worry.getId());
    }

    private void markReadByJdbc(String table, Long id) {
        jdbc.update("UPDATE " + table + " SET read_by_senior = TRUE WHERE id = ?", id);
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "홈선배");
        other = newUser(Role.SENIOR, "다른선배");
    }

    // ---------- 기본 응답 ----------

    @Test
    void 새_어르신의_홈은_닉네임과_빈_값을_래퍼로_준다() throws Exception {
        home(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SENIOR_200_1"))
                .andExpect(jsonPath("$.message").value("홈 정보 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.nickname").value(senior.getNickname()))
                .andExpect(jsonPath("$.data.unreadReceivedCount").value(0))
                .andExpect(jsonPath("$.data.woolStage", nullValue()))
                .andExpect(jsonPath("$.data.totalReplies").value(0))
                // 명세의 다섯 필드 외에 다른 값이 내려가지 않는다.
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    // ---------- 오늘 새 고민 수 ----------

    @Test
    void 오늘_올라온_읽을_수_있는_고민만_센다() throws Exception {
        long before = number("newWorryCountToday");

        // 공개 범위가 달라도, 내가 답장했어도 센다.
        Worry publicWorry = newWorry(WorryVisibility.PUBLIC);
        newWorry(WorryVisibility.SENIORS_ONLY);
        replyOn(publicWorry, senior);
        // 삭제하거나 신고로 숨긴 고민은 세지 않는다.
        Worry deleted = newWorry();
        deleted.delete();
        worries.saveAndFlush(deleted);
        Worry hidden = newWorry();
        jdbc.update("UPDATE worries SET hidden = TRUE WHERE id = ?", hidden.getId());
        // 어제 올라온 고민은 세지 않는다.
        createdAt(newWorry(), LocalDate.now().atStartOfDay().minusHours(1));

        assertThat(number("newWorryCountToday")).isEqualTo(before + 2);
    }

    @Test
    void 오늘_0시는_오늘이고_그_직전은_어제다() throws Exception {
        long before = number("newWorryCountToday");
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        createdAt(newWorry(), startOfToday);
        createdAt(newWorry(), startOfToday.minusSeconds(1));

        assertThat(number("newWorryCountToday")).isEqualTo(before + 1);
    }

    // ---------- 안 읽은 받은 마음 수 ----------

    @Test
    void 안_읽은_고마움과_후일담을_세고_삭제_숨김_읽은_마음과_다른_어르신의_것은_세지_않는다() throws Exception {
        // 센다: 안 읽은 고마움 하나, 안 읽은 후일담 하나
        Thanks unreadThanks = thanksRepository.saveAndFlush(
                Thanks.of(replyOn(newWorry(), senior), ThanksStamp.GRATEFUL, "고마워요"));
        followUpRepository.saveAndFlush(FollowUp.of(replyOn(newWorry(), senior), "후일담", WorryVisibility.SENIORS_ONLY));
        // 세지 않는다: 이미 읽은 마음
        Thanks readThanks = thanksRepository.saveAndFlush(
                Thanks.of(replyOn(newWorry(), senior), ThanksStamp.HELPED, null));
        markReadByJdbc("thanks", readThanks.getId());
        // 세지 않는다: 삭제한 답장, 신고로 숨긴 답장, 삭제된 고민, 숨겨진 고민의 마음
        Reply deletedReply = Reply.text(newWorry(), senior, "지움");
        deletedReply.delete();
        thanksRepository.saveAndFlush(Thanks.of(replies.saveAndFlush(deletedReply), ThanksStamp.CHEERED, null));
        Reply hiddenReply = Reply.text(newWorry(), senior, "숨김");
        hiddenReply.hide();
        followUpRepository.saveAndFlush(FollowUp.of(replies.saveAndFlush(hiddenReply), "숨긴 답장의 후일담", WorryVisibility.PUBLIC));
        Worry deletedWorry = newWorry();
        thanksRepository.saveAndFlush(Thanks.of(replyOn(deletedWorry, senior), ThanksStamp.HELPED, null));
        deletedWorry.delete();
        worries.saveAndFlush(deletedWorry);
        Worry hiddenWorry = newWorry();
        followUpRepository.saveAndFlush(FollowUp.of(replyOn(hiddenWorry, senior), "숨겨진 고민의 후일담", WorryVisibility.PUBLIC));
        jdbc.update("UPDATE worries SET hidden = TRUE WHERE id = ?", hiddenWorry.getId());
        // 세지 않는다: 다른 어르신이 받은 마음
        thanksRepository.saveAndFlush(Thanks.of(replyOn(newWorry(), other), ThanksStamp.GRATEFUL, "남의 마음"));

        home(senior).andExpect(jsonPath("$.data.unreadReceivedCount").value(2));
        assertThat(unreadThanks.getId()).isNotNull();
    }

    @Test
    void 홈을_조회해도_읽음_처리하지_않고_받은_마음_목록을_조회해야_0이_된다() throws Exception {
        thanksRepository.saveAndFlush(Thanks.of(replyOn(newWorry(), senior), ThanksStamp.GRATEFUL, "고마워요"));
        followUpRepository.saveAndFlush(FollowUp.of(replyOn(newWorry(), senior), "후일담", WorryVisibility.PUBLIC));

        // 홈을 여러 번 조회해도 그대로다.
        home(senior).andExpect(jsonPath("$.data.unreadReceivedCount").value(2));
        home(senior).andExpect(jsonPath("$.data.unreadReceivedCount").value(2));

        // 받은 마음 목록(WOOL-002)을 조회하면 목록에 담긴 마음이 읽음 처리되어 0이 된다.
        mockMvc.perform(get("/api/senior/received").header("X-User-Id", senior.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));
        home(senior).andExpect(jsonPath("$.data.unreadReceivedCount").value(0));
    }

    // 목록에 보이는 마음 수와 안 읽은 수가 같은 조건이라, 처음 조회할 때 둘이 같고 목록이 안 보이는 마음은 남지 않는다.
    @Test
    void 안_읽은_수는_받은_마음_목록에_보이는_수와_같다() throws Exception {
        thanksRepository.saveAndFlush(Thanks.of(replyOn(newWorry(), senior), ThanksStamp.GRATEFUL, null));
        Reply hiddenReply = Reply.text(newWorry(), senior, "숨김");
        hiddenReply.hide();
        thanksRepository.saveAndFlush(Thanks.of(replies.saveAndFlush(hiddenReply), ThanksStamp.HELPED, null));

        home(senior).andExpect(jsonPath("$.data.unreadReceivedCount").value(1));
        mockMvc.perform(get("/api/senior/received").header("X-User-Id", senior.getId()))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        home(senior).andExpect(jsonPath("$.data.unreadReceivedCount").value(0));
    }

    // ---------- 털 단계와 누적 답장 수 ----------

    @Test
    void 누적_답장_수에_따라_털_단계가_정해지고_숨김_삭제_답장은_세지_않는다() throws Exception {
        for (int i = 0; i < 1; i++) replyOn(newWorry(), senior);
        home(senior).andExpect(jsonPath("$.data.totalReplies").value(1)).andExpect(jsonPath("$.data.woolStage").value("BALL"));

        for (int i = 0; i < 4; i++) replyOn(newWorry(), senior);
        home(senior).andExpect(jsonPath("$.data.totalReplies").value(5)).andExpect(jsonPath("$.data.woolStage").value("BALL"));

        replyOn(newWorry(), senior);
        home(senior).andExpect(jsonPath("$.data.totalReplies").value(6)).andExpect(jsonPath("$.data.woolStage").value("SCARF"));

        for (int i = 0; i < 13; i++) replyOn(newWorry(), senior);
        home(senior).andExpect(jsonPath("$.data.totalReplies").value(19)).andExpect(jsonPath("$.data.woolStage").value("SCARF"));

        replyOn(newWorry(), senior);
        home(senior).andExpect(jsonPath("$.data.totalReplies").value(20)).andExpect(jsonPath("$.data.woolStage").value("SWEATER"));

        // 숨기거나 삭제한 답장은 세지 않는다.
        Reply hidden = Reply.text(newWorry(), senior, "숨김");
        hidden.hide();
        replies.saveAndFlush(hidden);
        Reply deleted = Reply.text(newWorry(), senior, "지움");
        deleted.delete();
        replies.saveAndFlush(deleted);
        replyOn(newWorry(), other);
        home(senior).andExpect(jsonPath("$.data.totalReplies").value(20));
    }

    @Test
    void 털_기록_조회와_같은_누적_답장_수를_준다() throws Exception {
        for (int i = 0; i < 7; i++) replyOn(newWorry(), senior);

        home(senior).andExpect(jsonPath("$.data.totalReplies").value(7));
        mockMvc.perform(get("/api/senior/wool").header("X-User-Id", senior.getId()))
                .andExpect(jsonPath("$.data.totalReplies").value(7));
    }

    // ---------- 권한 ----------

    @Test
    void 헤더가_없으면_401_청년이면_403이다() throws Exception {
        home(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        home(youth).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }
}
