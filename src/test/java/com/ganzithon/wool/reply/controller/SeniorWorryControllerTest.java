package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REPLY-002 고민 목록(어르신), REPLY-009 고민 상세(어르신)
@AutoConfigureMockMvc
@Transactional
class SeniorWorryControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private User youth;
    private User senior;
    private User other;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, AgeGroup age, String prefix) {
        return users.saveAndFlush(User.join(role, age, name(prefix)));
    }

    private Worry newWorry(String title, WorryVisibility visibility) {
        return worries.saveAndFlush(Worry.of(youth, title, "본문 " + title, WorryTag.CAREER, visibility));
    }

    // 작성 시각을 직접 정해 정렬을 확인한다.
    private Worry newWorryAt(String title, LocalDateTime createdAt) {
        Worry worry = newWorry(title, WorryVisibility.PUBLIC);
        jdbc.update("UPDATE worries SET created_at = ? WHERE id = ?", createdAt, worry.getId());
        return worry;
    }

    private Reply reply(Worry worry, User by, boolean hidden, boolean deleted) {
        Reply reply = Reply.text(worry, by, "답장 " + UUID.randomUUID().toString().substring(0, 4));
        if (hidden) reply.hide();
        if (deleted) reply.delete();
        return replies.saveAndFlush(reply);
    }

    private void hideWorry(Worry worry) {
        jdbc.update("UPDATE worries SET hidden = TRUE WHERE id = ?", worry.getId());
        entityManager.clear();
    }

    private void deleteWorry(Worry worry) {
        jdbc.update("UPDATE worries SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", worry.getId());
        entityManager.clear();
    }

    private ResultActions list(User who) throws Exception {
        var request = get("/api/senior/worries");
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    private ResultActions detail(User who, Long worryId) throws Exception {
        var request = get("/api/senior/worries/{worryId}", worryId);
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, AgeGroup.AGE_20S, "청년");
        senior = newUser(Role.SENIOR, AgeGroup.AGE_60S, "선배");
        other = newUser(Role.SENIOR, AgeGroup.AGE_70S_PLUS, "다른선배");
    }

    // ================= REPLY-002 목록 =================

    @Test
    void 목록은_래퍼로_내려주고_본문과_작성자_닉네임은_내려주지_않는다() throws Exception {
        Worry worry = newWorry("첫 고민", WorryVisibility.PUBLIC);

        list(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SENIOR_200_2"))
                .andExpect(jsonPath("$.message").value("고민 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.waitingCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].worryId").value(worry.getId()))
                .andExpect(jsonPath("$.data.items[0].title").value("첫 고민"))
                .andExpect(jsonPath("$.data.items[0].tag").value("CAREER"))
                .andExpect(jsonPath("$.data.items[0].authorAgeGroup").value("20S"))
                .andExpect(jsonPath("$.data.items[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.items[0].replyCount").value(0))
                .andExpect(jsonPath("$.data.items[0].repliedByMe").value(false))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists())
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].preview").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].author").doesNotExist());
    }

    @Test
    void 고민이_없으면_빈_목록과_waitingCount_0을_준다() throws Exception {
        list(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waitingCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 답장_0통인_고민이_먼저고_그_안에서_각각_최신순이다() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 12, 0);
        Worry oldNoReply = newWorryAt("오래된 0통", base);
        Worry newNoReply = newWorryAt("최근 0통", base.plusDays(3));
        Worry oldReplied = newWorryAt("오래된 답장 있음", base.plusDays(1));
        Worry newReplied = newWorryAt("최근 답장 있음", base.plusDays(4));
        reply(oldReplied, other, false, false);
        reply(newReplied, other, false, false);

        list(senior)
                .andExpect(jsonPath("$.data.items[0].worryId").value(newNoReply.getId()))
                .andExpect(jsonPath("$.data.items[1].worryId").value(oldNoReply.getId()))
                .andExpect(jsonPath("$.data.items[2].worryId").value(newReplied.getId()))
                .andExpect(jsonPath("$.data.items[3].worryId").value(oldReplied.getId()))
                .andExpect(jsonPath("$.data.waitingCount").value(2));
    }

    @Test
    void 작성_시각이_같으면_나중에_쓴_고민이_먼저다() throws Exception {
        LocalDateTime same = LocalDateTime.of(2026, 11, 1, 12, 0);
        Worry first = newWorryAt("먼저", same);
        Worry second = newWorryAt("나중", same);

        list(senior)
                .andExpect(jsonPath("$.data.items[0].worryId").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].worryId").value(first.getId()));
    }

    @Test
    void 답장_수는_삭제하거나_숨긴_답장을_빼고_센다() throws Exception {
        Worry worry = newWorry("답장 섞임", WorryVisibility.PUBLIC);
        reply(worry, other, false, false);
        reply(worry, newUser(Role.SENIOR, AgeGroup.AGE_50S, "숨김선배"), true, false);
        reply(worry, newUser(Role.SENIOR, AgeGroup.AGE_50S, "삭제선배"), false, true);

        list(senior)
                .andExpect(jsonPath("$.data.items[0].replyCount").value(1))
                .andExpect(jsonPath("$.data.waitingCount").value(0));
    }

    @Test
    void 유효한_답장이_모두_사라지면_다시_0통으로_먼저_나온다() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 12, 0);
        Worry allGone = newWorryAt("답장이 모두 사라짐", base);
        Worry replied = newWorryAt("답장 있음", base.plusDays(1));
        reply(allGone, other, true, false);
        reply(replied, other, false, false);

        list(senior)
                .andExpect(jsonPath("$.data.items[0].worryId").value(allGone.getId()))
                .andExpect(jsonPath("$.data.items[0].replyCount").value(0))
                .andExpect(jsonPath("$.data.items[1].worryId").value(replied.getId()));
    }

    @Test
    void 내가_답장한_고민은_repliedByMe가_true이고_삭제하거나_숨긴_내_답장도_포함한다() throws Exception {
        Worry normal = newWorry("내 답장", WorryVisibility.PUBLIC);
        Worry hiddenMine = newWorry("숨겨진 내 답장", WorryVisibility.PUBLIC);
        Worry deletedMine = newWorry("삭제한 내 답장", WorryVisibility.PUBLIC);
        Worry others = newWorry("남의 답장", WorryVisibility.PUBLIC);
        reply(normal, senior, false, false);
        reply(hiddenMine, senior, true, false);
        reply(deletedMine, senior, false, true);
        reply(others, other, false, false);

        list(senior)
                .andExpect(jsonPath("$.data.items[?(@.worryId==" + normal.getId() + ")].repliedByMe").value(true))
                .andExpect(jsonPath("$.data.items[?(@.worryId==" + hiddenMine.getId() + ")].repliedByMe").value(true))
                .andExpect(jsonPath("$.data.items[?(@.worryId==" + deletedMine.getId() + ")].repliedByMe").value(true))
                .andExpect(jsonPath("$.data.items[?(@.worryId==" + others.getId() + ")].repliedByMe").value(false));
    }

    @Test
    void 신고로_숨겨졌거나_삭제된_고민은_목록에서_빠진다() throws Exception {
        Worry visible = newWorry("보이는 고민", WorryVisibility.PUBLIC);
        Worry hidden = newWorry("숨겨진 고민", WorryVisibility.PUBLIC);
        Worry deleted = newWorry("삭제된 고민", WorryVisibility.PUBLIC);
        hideWorry(hidden);
        deleteWorry(deleted);

        list(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].worryId").value(visible.getId()))
                .andExpect(jsonPath("$.data.waitingCount").value(1));
    }

    @Test
    void 전체_공개와_선배만_보기_고민이_모두_보인다() throws Exception {
        newWorry("공개", WorryVisibility.PUBLIC);
        newWorry("선배만", WorryVisibility.SENIORS_ONLY);

        list(senior)
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.title=='공개')].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.items[?(@.title=='선배만')].visibility").value("SENIORS_ONLY"));
    }

    @Test
    void 제재_중인_어르신도_목록은_볼_수_있다() throws Exception {
        senior.applySanction(SanctionLevel.BANNED, null);
        users.saveAndFlush(senior);
        newWorry("보이는 고민", WorryVisibility.PUBLIC);

        list(senior).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 목록은_헤더가_없으면_401_청년이면_403이다() throws Exception {
        list(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        list(youth).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }

    // ================= REPLY-009 상세 =================

    @Test
    void 상세는_원문_그대로_내려주고_작성자는_나이대만_보여준다() throws Exception {
        Worry worry = newWorry("첫 고민", WorryVisibility.PUBLIC);

        detail(senior, worry.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SENIOR_200_4"))
                .andExpect(jsonPath("$.message").value("고민 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.worryId").value(worry.getId()))
                .andExpect(jsonPath("$.data.title").value("첫 고민"))
                .andExpect(jsonPath("$.data.content").value("본문 첫 고민"))
                .andExpect(jsonPath("$.data.tag").value("CAREER"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.authorAgeGroup").value("20S"))
                .andExpect(jsonPath("$.data.repliedByMe").value(false))
                .andExpect(jsonPath("$.data.canReply").value(true))
                .andExpect(jsonPath("$.data.restrictedUntil").value(nullValue()))
                .andExpect(jsonPath("$.data.otherReplies.length()").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.author").doesNotExist())
                .andExpect(jsonPath("$.data.likeCount").doesNotExist())
                .andExpect(jsonPath("$.data.followUp").doesNotExist());
    }

    @Test
    void 공개_고민은_다른_선배의_답장을_오래된_순으로_보여주고_내_답장과_삭제_숨김_답장은_뺀다() throws Exception {
        Worry worry = newWorry("공개 고민", WorryVisibility.PUBLIC);
        User third = newUser(Role.SENIOR, AgeGroup.AGE_50S, "셋째선배");
        Reply first = reply(worry, other, false, false);
        Reply second = reply(worry, third, false, false);
        reply(worry, senior, false, false);
        reply(worry, newUser(Role.SENIOR, AgeGroup.AGE_50S, "숨김선배"), true, false);
        reply(worry, newUser(Role.SENIOR, AgeGroup.AGE_50S, "삭제선배"), false, true);
        jdbc.update("UPDATE replies SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 11, 1, 10, 0), second.getId());
        jdbc.update("UPDATE replies SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 11, 2, 10, 0), first.getId());
        entityManager.clear();

        detail(senior, worry.getId())
                .andExpect(jsonPath("$.data.otherReplies.length()").value(2))
                .andExpect(jsonPath("$.data.otherReplies[0].replyId").value(second.getId()))
                .andExpect(jsonPath("$.data.otherReplies[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.otherReplies[0].content").value(second.getContent()))
                .andExpect(jsonPath("$.data.otherReplies[0].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.otherReplies[0].senior.nickname").value(third.getNickname()))
                .andExpect(jsonPath("$.data.otherReplies[0].senior.ageGroup").value("50S"))
                .andExpect(jsonPath("$.data.otherReplies[0].createdAt").exists())
                .andExpect(jsonPath("$.data.otherReplies[1].replyId").value(first.getId()))
                .andExpect(jsonPath("$.data.otherReplies[1].senior.ageGroup").value("70S_PLUS"));
    }

    @Test
    void 선배만_보기_고민은_다른_선배의_답장을_보여주지_않는다() throws Exception {
        Worry worry = newWorry("선배만", WorryVisibility.SENIORS_ONLY);
        reply(worry, other, false, false);

        detail(senior, worry.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("SENIORS_ONLY"))
                .andExpect(jsonPath("$.data.otherReplies.length()").value(0));
    }

    @Test
    void 손편지_답장은_이미지_주소를_주고_본문은_null이다() throws Exception {
        Worry worry = newWorry("공개 고민", WorryVisibility.PUBLIC);
        replies.saveAndFlush(Reply.letter(worry, other, "/files/letters/" + UUID.randomUUID() + ".png"));

        detail(senior, worry.getId())
                .andExpect(jsonPath("$.data.otherReplies[0].type").value("LETTER"))
                .andExpect(jsonPath("$.data.otherReplies[0].content").value(nullValue()))
                .andExpect(jsonPath("$.data.otherReplies[0].imageUrl").value(org.hamcrest.Matchers.startsWith("/files/letters/")));
    }

    @Test
    void 이미_답장했으면_canReply가_false다_삭제하거나_숨긴_내_답장도_마찬가지다() throws Exception {
        Worry normal = newWorry("내 답장", WorryVisibility.PUBLIC);
        Worry hiddenMine = newWorry("숨겨진 내 답장", WorryVisibility.PUBLIC);
        Worry deletedMine = newWorry("삭제한 내 답장", WorryVisibility.PUBLIC);
        reply(normal, senior, false, false);
        reply(hiddenMine, senior, true, false);
        reply(deletedMine, senior, false, true);

        for (Worry worry : new Worry[]{normal, hiddenMine, deletedMine}) {
            detail(senior, worry.getId())
                    .andExpect(jsonPath("$.data.repliedByMe").value(true))
                    .andExpect(jsonPath("$.data.canReply").value(false))
                    .andExpect(jsonPath("$.data.restrictedUntil").value(nullValue()));
        }
    }

    @Test
    void 일주일_제한_중이면_canReply가_false이고_해제_시각을_준다() throws Exception {
        LocalDateTime until = LocalDateTime.now().plusDays(3).withNano(0);
        senior.applySanction(SanctionLevel.RESTRICTED, until);
        users.saveAndFlush(senior);
        Worry worry = newWorry("고민", WorryVisibility.PUBLIC);

        detail(senior, worry.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReply").value(false))
                .andExpect(jsonPath("$.data.repliedByMe").value(false))
                .andExpect(jsonPath("$.data.restrictedUntil").exists());
    }

    @Test
    void 영구_제한이면_canReply가_false이고_해제_시각은_null이다() throws Exception {
        senior.applySanction(SanctionLevel.BANNED, null);
        users.saveAndFlush(senior);
        Worry worry = newWorry("고민", WorryVisibility.PUBLIC);

        detail(senior, worry.getId())
                .andExpect(jsonPath("$.data.canReply").value(false))
                .andExpect(jsonPath("$.data.restrictedUntil").value(nullValue()));
    }

    @Test
    void 제한_시각이_지났으면_상태가_아직_풀리지_않았어도_답장할_수_있다() throws Exception {
        senior.applySanction(SanctionLevel.RESTRICTED, LocalDateTime.now().minusMinutes(1));
        users.saveAndFlush(senior);
        Worry worry = newWorry("고민", WorryVisibility.PUBLIC);

        detail(senior, worry.getId())
                .andExpect(jsonPath("$.data.canReply").value(true))
                .andExpect(jsonPath("$.data.restrictedUntil").value(nullValue()));
    }

    @Test
    void 경고_단계는_답장을_막지_않는다() throws Exception {
        senior.applySanction(SanctionLevel.WARNED, null);
        users.saveAndFlush(senior);
        Worry worry = newWorry("고민", WorryVisibility.PUBLIC);

        detail(senior, worry.getId()).andExpect(jsonPath("$.data.canReply").value(true));
    }

    @Test
    void 숨겨졌거나_삭제됐거나_없는_고민은_404다() throws Exception {
        Worry hidden = newWorry("숨김", WorryVisibility.PUBLIC);
        Worry deleted = newWorry("삭제", WorryVisibility.PUBLIC);
        hideWorry(hidden);
        deleteWorry(deleted);

        detail(senior, hidden.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
        detail(senior, deleted.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
        detail(senior, 999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
    }

    @Test
    void 상세는_헤더가_없으면_401_청년이면_403이다() throws Exception {
        Worry worry = newWorry("고민", WorryVisibility.PUBLIC);

        detail(null, worry.getId()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        detail(youth, worry.getId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }
}
