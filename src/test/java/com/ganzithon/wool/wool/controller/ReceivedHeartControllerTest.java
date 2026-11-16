package com.ganzithon.wool.wool.controller;

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
import com.ganzithon.wool.wool.repository.ReceivedHeartRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// WOOL-002 받은 마음 목록
@AutoConfigureMockMvc
@Transactional
class ReceivedHeartControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ThanksRepository thanksRepository;
    @Autowired private FollowUpRepository followUpRepository;
    @Autowired private ReceivedHeartRepository receivedHeartRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.ganzithon.wool.report.service.WorryReportService worryReportService;

    private User youth;
    private User senior;
    private User other;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private Worry newWorry(String title) {
        return worries.saveAndFlush(Worry.of(youth, title, "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private Reply replyOn(Worry worry, User by) {
        return replies.saveAndFlush(Reply.text(worry, by, "답장"));
    }

    private Thanks thanks(Reply reply, ThanksStamp stamp, String message) {
        return thanksRepository.saveAndFlush(Thanks.of(reply, stamp, message));
    }

    private FollowUp followUp(Reply reply, String content, WorryVisibility visibility) {
        return followUpRepository.saveAndFlush(FollowUp.of(reply, content, visibility));
    }

    private boolean thanksRead(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT read_by_senior FROM thanks WHERE id = ?", Boolean.class, id));
    }

    private boolean followUpRead(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT read_by_senior FROM follow_ups WHERE id = ?", Boolean.class, id));
    }

    private ResultActions received(User who) throws Exception {
        var request = get("/api/senior/received");
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "선배");
        other = newUser(Role.SENIOR, "다른선배");
    }

    // ---------- 목록 ----------

    @Test
    void 받은_마음이_없으면_빈_목록이다() throws Exception {
        received(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("WOOL_200_2"))
                .andExpect(jsonPath("$.message").value("받은 마음 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 고마움과_후일담을_최신순으로_섞어_종류와_도장과_내용을_준다() throws Exception {
        Reply first = replyOn(newWorry("첫 직장 고민"), senior);
        Reply second = replyOn(newWorry("두 번째 고민"), senior);
        Reply third = replyOn(newWorry("세 번째 고민"), senior);
        Thanks firstHeart = thanks(first, ThanksStamp.GRATEFUL, "선배님 덕분에 다시 힘냈어요.");
        FollowUp secondHeart = followUp(second, "버텨봤더니 마음이 편해졌어요.", WorryVisibility.SENIORS_ONLY);
        Thanks thirdHeart = thanks(third, ThanksStamp.CHEERED, null);
        jdbc.update("UPDATE thanks SET created_at = ? WHERE id = ?", java.time.LocalDateTime.of(2026, 1, 1, 10, 0), firstHeart.getId());
        jdbc.update("UPDATE follow_ups SET created_at = ? WHERE id = ?", java.time.LocalDateTime.of(2026, 1, 1, 10, 1), secondHeart.getId());
        jdbc.update("UPDATE thanks SET created_at = ? WHERE id = ?", java.time.LocalDateTime.of(2026, 1, 1, 10, 2), thirdHeart.getId());

        received(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // 가장 나중에 받은 한마디 없는 고마움이 맨 앞이다.
                .andExpect(jsonPath("$.data.items[0].kind").value("THANKS"))
                .andExpect(jsonPath("$.data.items[0].replyId").value(third.getId()))
                .andExpect(jsonPath("$.data.items[0].worryTitle").value("세 번째 고민"))
                .andExpect(jsonPath("$.data.items[0].stamp").value("CHEERED"))
                .andExpect(jsonPath("$.data.items[0].content").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].createdAt", endsWith("+09:00")))
                // 후일담은 도장이 없다.
                .andExpect(jsonPath("$.data.items[1].kind").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.items[1].replyId").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].stamp").value((Object) null))
                .andExpect(jsonPath("$.data.items[1].content").value("버텨봤더니 마음이 편해졌어요."))
                .andExpect(jsonPath("$.data.items[2].kind").value("THANKS"))
                .andExpect(jsonPath("$.data.items[2].stamp").value("GRATEFUL"))
                .andExpect(jsonPath("$.data.items[2].content").value("선배님 덕분에 다시 힘냈어요."))
                // 명세의 여섯 필드 외에 다른 값이 내려가지 않는다.
                .andExpect(jsonPath("$.data.items[0].length()").value(6));
    }

    @Test
    void 한_답장에_고마움과_후일담이_모두_있으면_두_항목으로_나온다() throws Exception {
        Reply reply = replyOn(newWorry("한 고민"), senior);
        Thanks thank = thanks(reply, ThanksStamp.HELPED, "고마워요");
        FollowUp follow = followUp(reply, "그 뒤 이야기", WorryVisibility.PUBLIC);
        jdbc.update("UPDATE thanks SET created_at = ? WHERE id = ?", java.time.LocalDateTime.of(2026, 1, 1, 10, 0), thank.getId());
        jdbc.update("UPDATE follow_ups SET created_at = ? WHERE id = ?", java.time.LocalDateTime.of(2026, 1, 1, 10, 1), follow.getId());

        received(senior)
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].kind").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.data.items[1].kind").value("THANKS"));
    }

    @Test
    void 후일담은_공개_범위와_상관없이_어르신에게_보인다() throws Exception {
        followUp(replyOn(newWorry("선배님께만"), senior), "비공개 후일담", WorryVisibility.SENIORS_ONLY);
        followUp(replyOn(newWorry("전체 공개"), senior), "공개 후일담", WorryVisibility.PUBLIC);

        received(senior).andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void 내_답장에_받은_마음만_보이고_다른_어르신의_것은_섞이지_않는다() throws Exception {
        Reply mine = replyOn(newWorry("내 답장"), senior);
        Reply theirs = replyOn(newWorry("남의 답장"), other);
        thanks(mine, ThanksStamp.GRATEFUL, "내 고마움");
        thanks(theirs, ThanksStamp.GRATEFUL, "남의 고마움");
        followUp(theirs, "남의 후일담", WorryVisibility.PUBLIC);

        received(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("내 고마움"));
    }

    @Test
    void 삭제하거나_신고로_숨겨진_답장과_삭제하거나_숨겨진_고민의_마음은_제외한다() throws Exception {
        Reply visible = replyOn(newWorry("보이는 고민"), senior);
        thanks(visible, ThanksStamp.GRATEFUL, "보이는 마음");

        Reply deletedReply = Reply.text(newWorry("삭제한 답장의 고민"), senior, "지움");
        deletedReply.delete();
        deletedReply = replies.saveAndFlush(deletedReply);
        thanks(deletedReply, ThanksStamp.GRATEFUL, "삭제한 답장의 마음");

        Reply hiddenReply = Reply.text(newWorry("숨긴 답장의 고민"), senior, "숨김");
        hiddenReply.hide();
        hiddenReply = replies.saveAndFlush(hiddenReply);
        followUp(hiddenReply, "숨긴 답장의 후일담", WorryVisibility.PUBLIC);

        Worry deletedWorry = newWorry("삭제된 고민");
        thanks(replyOn(deletedWorry, senior), ThanksStamp.HELPED, "삭제된 고민의 마음");
        deletedWorry.delete();
        worries.saveAndFlush(deletedWorry);

        Worry hiddenWorry = newWorry("숨겨진 고민");
        followUp(replyOn(hiddenWorry, senior), "숨겨진 고민의 후일담", WorryVisibility.PUBLIC);
        jdbc.update("UPDATE worries SET hidden = TRUE WHERE id = ?", hiddenWorry.getId());

        received(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("보이는 마음"));
    }

    // ---------- 읽음 처리 ----------

    @Test
    void 조회하면_보낸_마음이_모두_읽음_처리되고_다시_조회해도_같은_목록이다() throws Exception {
        Thanks t = thanks(replyOn(newWorry("고마움 고민"), senior), ThanksStamp.GRATEFUL, "고마워요");
        FollowUp f = followUp(replyOn(newWorry("후일담 고민"), senior), "후일담", WorryVisibility.SENIORS_ONLY);
        assertThat(thanksRead(t.getId())).isFalse();
        assertThat(followUpRead(f.getId())).isFalse();

        received(senior).andExpect(jsonPath("$.data.items.length()").value(2));

        assertThat(thanksRead(t.getId())).isTrue();
        assertThat(followUpRead(f.getId())).isTrue();
        // 읽은 마음도 목록에서 사라지지 않는다.
        received(senior).andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void 다른_어르신의_안_읽은_마음과_목록에_안_보이는_마음은_읽음_처리하지_않는다() throws Exception {
        Thanks mine = thanks(replyOn(newWorry("내 고민"), senior), ThanksStamp.GRATEFUL, "내 마음");
        Thanks theirs = thanks(replyOn(newWorry("남의 고민"), other), ThanksStamp.GRATEFUL, "남의 마음");
        Reply deleted = Reply.text(newWorry("삭제한 답장"), senior, "지움");
        deleted.delete();
        Thanks onDeleted = thanks(replies.saveAndFlush(deleted), ThanksStamp.GRATEFUL, "삭제한 답장의 마음");

        received(senior).andExpect(status().isOk());

        assertThat(thanksRead(mine.getId())).isTrue();
        assertThat(thanksRead(theirs.getId())).isFalse();
        assertThat(thanksRead(onDeleted.getId())).isFalse();
    }

    // 목록을 읽은 뒤 읽음 표시 전에 새 마음이 도착해도, 보낸 id만 바꾸므로 새 마음은 안 읽은 채로 남는다.
    @Test
    void 읽음_표시는_목록에_담아_보낸_마음만_바꾼다() {
        Reply reply = replyOn(newWorry("고민"), senior);
        Thanks sent = thanks(reply, ThanksStamp.GRATEFUL, "보낸 마음");

        var rows = receivedHeartRepository.findBySeniorId(senior.getId());
        Reply laterReply = replyOn(newWorry("나중 고민"), senior);
        Thanks arrivedLater = thanks(laterReply, ThanksStamp.HELPED, "나중에 도착한 마음");
        receivedHeartRepository.markRead(rows.stream().map(ReceivedHeartRepository.Row::itemId).toList(), List.of());

        assertThat(rows).hasSize(1);
        assertThat(thanksRead(sent.getId())).isTrue();
        assertThat(thanksRead(arrivedLater.getId())).isFalse();
    }

    @Test
    void 읽음_표시는_고마움과_후일담을_따로_센다() {
        // 같은 id라도 고마움 표와 후일담 표는 별개다. 후일담 id로 고마움을 읽음 처리하지 않는다.
        Reply reply = replyOn(newWorry("고민"), senior);
        Thanks t = thanks(reply, ThanksStamp.GRATEFUL, "고마움");
        FollowUp f = followUp(reply, "후일담", WorryVisibility.PUBLIC);

        receivedHeartRepository.markRead(List.of(), List.of(f.getId()));

        assertThat(thanksRead(t.getId())).isFalse();
        assertThat(followUpRead(f.getId())).isTrue();
    }

    // ---------- 권한 ----------

    @Test
    void 헤더가_없으면_401_청년이면_403이고_읽음_처리도_하지_않는다() throws Exception {
        Thanks t = thanks(replyOn(newWorry("고민"), senior), ThanksStamp.GRATEFUL, "고마움");

        received(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        received(youth).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));

        assertThat(thanksRead(t.getId())).isFalse();
    }
    @Test
    void 고민_신고_후_받은_마음은_목록에서_빠지고_읽음_처리되지_않는다() throws Exception {
        Worry worry = newWorry("신고할 고민");
        Reply reply = replyOn(worry, senior);
        Thanks thank = thanks(reply, ThanksStamp.GRATEFUL, "감사해요");
        FollowUp follow = followUp(reply, "후일담", WorryVisibility.PUBLIC);
        worryReportService.report(senior, worry.getId(), com.ganzithon.wool.report.entity.WorryReportReason.AD);
        worries.flush();
        received(senior).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(thanksRead(thank.getId())).isFalse();
        assertThat(followUpRead(follow.getId())).isFalse();
    }

}
