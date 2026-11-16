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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REPLY-008 답장 수정
@AutoConfigureMockMvc
@Transactional
class ReplyUpdateControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ThanksRepository thanksRepository;
    @Autowired private FollowUpRepository followUpRepository;
    @Autowired private EntityManager entityManager;

    private User youth;
    private User senior;
    private User other;
    private Reply reply;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private Worry newWorry() {
        return worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private ResultActions update(User who, Long replyId, String body) throws Exception {
        var request = patch("/api/replies/{replyId}", replyId).contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    private ResultActions updateContent(User who, Long replyId, String content) throws Exception {
        return update(who, replyId, "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}");
    }

    private Reply reload(Long replyId) {
        entityManager.flush();
        entityManager.clear();
        return replies.findById(replyId).orElseThrow();
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "선배");
        other = newUser(Role.SENIOR, "다른선배");
        reply = replies.saveAndFlush(Reply.text(newWorry(), senior, "처음 답장"));
    }

    // ---------- 수정 ----------

    @Test
    void 수정하면_200과_래퍼로_수정한_답장과_수정_시각을_주고_내용이_바뀐다() throws Exception {
        updateContent(senior, reply.getId(), "나도 그맘때 많이 헤맸어. 지금도 충분히 잘하고 있단다.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("REPLY_200_8"))
                .andExpect(jsonPath("$.message").value("답장을 수정했습니다."))
                .andExpect(jsonPath("$.data.replyId").value(reply.getId()))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.length()").value(2));

        assertThat(reload(reply.getId()).getContent()).isEqualTo("나도 그맘때 많이 헤맸어. 지금도 충분히 잘하고 있단다.");
    }

    @Test
    void 말_답장도_고칠_수_있다() throws Exception {
        Reply voice = replies.saveAndFlush(Reply.voice(newWorry(), senior, "말로 한 답장"));

        updateContent(senior, voice.getId(), "다시 확인한 답장").andExpect(status().isOk());

        assertThat(reload(voice.getId()).getContent()).isEqualTo("다시 확인한 답장");
    }

    @Test
    void 내용은_다듬지_않고_앞뒤_공백과_줄바꿈까지_그대로_저장한다() throws Exception {
        update(senior, reply.getId(), "{\"content\":\"  첫 줄\\n둘째 줄  \"}").andExpect(status().isOk());

        assertThat(reload(reply.getId()).getContent()).isEqualTo("  첫 줄\n둘째 줄  ");
    }

    @Test
    void 이모지는_한_글자로_세서_천_글자까지_받고_천한_글자는_400이다() throws Exception {
        String thousand = "😀".repeat(1000);
        updateContent(senior, reply.getId(), thousand).andExpect(status().isOk());
        assertThat(reload(reply.getId()).getContent()).isEqualTo(thousand);

        updateContent(senior, reply.getId(), "😀".repeat(1001))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        assertThat(reload(reply.getId()).getContent()).isEqualTo(thousand);
    }

    @Test
    void 같은_내용으로_다시_보내도_200이고_수정_시각은_그대로다() throws Exception {
        var before = reload(reply.getId()).getUpdatedAt();

        updateContent(senior, reply.getId(), "처음 답장").andExpect(status().isOk());

        Reply after = reload(reply.getId());
        assertThat(after.getContent()).isEqualTo("처음 답장");
        assertThat(after.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    void 내용이_바뀌면_수정_시각이_갱신되고_응답의_수정_시각과_같다() throws Exception {
        var before = reload(reply.getId()).getUpdatedAt();
        Thread.sleep(20);

        String responseBody = updateContent(senior, reply.getId(), "고친 답장")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var after = reload(reply.getId()).getUpdatedAt();
        assertThat(after).isAfter(before);
        // 응답의 updatedAt도 수정 전 값이 아니라 새 수정 시각이어야 한다. (저장하기 전의 옛 값을 돌려주면 여기서 걸린다)
        String responseUpdatedAt = responseBody.replaceAll(".*\"updatedAt\":\"([^\"]+)\".*", "$1");
        assertThat(OffsetDateTime.parse(responseUpdatedAt).toLocalDateTime()).isAfter(before);
    }

    @Test
    void 수정해도_누적_답장_수는_그대로다() throws Exception {
        replies.saveAndFlush(Reply.text(newWorry(), senior, "다른 답장"));
        long before = replies.countVisibleBySeniorId(senior.getId());

        updateContent(senior, reply.getId(), "고친 답장").andExpect(status().isOk());

        assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(before);
    }

    // ---------- 요청 검사 400 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"content\":null}",
            "{\"content\":\"\"}",
            "{\"content\":\"   \"}",
            "{\"content\":123}",
            "{\"content\":true}",
            "{\"content\":[\"글\"]}",
            "이건 JSON이 아니에요",
            ""})
    void 내용이_없거나_공백이거나_문자열이_아니면_400이고_바뀌지_않는다(String body) throws Exception {
        update(senior, reply.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

        assertThat(reload(reply.getId()).getContent()).isEqualTo("처음 답장");
    }

    @Test
    void 천_글자를_넘으면_400이다() throws Exception {
        updateContent(senior, reply.getId(), "가".repeat(1001)).andExpect(status().isBadRequest());
        updateContent(senior, reply.getId(), "가".repeat(1000)).andExpect(status().isOk());
    }

    // ---------- 권한과 대상 ----------

    @Test
    void 헤더가_없으면_401_청년이면_403이다() throws Exception {
        updateContent(null, reply.getId(), "고친 답장")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        updateContent(youth, reply.getId(), "고친 답장")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));

        assertThat(reload(reply.getId()).getContent()).isEqualTo("처음 답장");
    }

    @Test
    void 다른_어르신의_답장은_403이고_바뀌지_않는다() throws Exception {
        updateContent(other, reply.getId(), "남의 답장을 고침")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLY_403_004"))
                .andExpect(jsonPath("$.message").value("수정할 권한이 없습니다."));

        assertThat(reload(reply.getId()).getContent()).isEqualTo("처음 답장");
    }

    @Test
    void 없거나_삭제했거나_신고로_숨겨진_답장은_404다() throws Exception {
        updateContent(senior, 999_999L, "고침").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));

        Reply deleted = Reply.text(newWorry(), senior, "지운 답장");
        deleted.delete();
        deleted = replies.saveAndFlush(deleted);
        updateContent(senior, deleted.getId(), "고침").andExpect(status().isNotFound());

        Reply hidden = Reply.text(newWorry(), senior, "숨긴 답장");
        hidden.hide();
        hidden = replies.saveAndFlush(hidden);
        updateContent(senior, hidden.getId(), "고침")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));
        assertThat(reload(hidden.getId()).getContent()).isEqualTo("숨긴 답장");
    }

    // ---------- 손편지, 고마움·후일담 409 ----------

    @Test
    void 손편지_답장은_409이다() throws Exception {
        Reply letter = replies.saveAndFlush(Reply.letter(newWorry(), senior, "/files/letters/a.jpg"));

        updateContent(senior, letter.getId(), "글로 바꾸기")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLY_409_003"))
                .andExpect(jsonPath("$.message").value("손편지 답장은 수정할 수 없어요."));
        assertThat(reload(letter.getId()).getContent()).isNull();
    }

    @Test
    void 고마움을_받은_답장은_409이고_바뀌지_않는다() throws Exception {
        thanksRepository.saveAndFlush(Thanks.of(reply, ThanksStamp.GRATEFUL, "고마워요"));

        updateContent(senior, reply.getId(), "고치기")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLY_409_002"))
                .andExpect(jsonPath("$.message").value("이미 고마움을 받은 답장은 수정할 수 없어요."));
        assertThat(reload(reply.getId()).getContent()).isEqualTo("처음 답장");
    }

    @Test
    void 후일담만_받은_답장도_409이다() throws Exception {
        followUpRepository.saveAndFlush(FollowUp.of(reply, "그 뒤 이야기", WorryVisibility.SENIORS_ONLY));

        updateContent(senior, reply.getId(), "고치기")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPLY_409_002"));
        assertThat(reload(reply.getId()).getContent()).isEqualTo("처음 답장");
    }

    @Test
    void 다른_답장이_고마움을_받았어도_이_답장은_고칠_수_있다() throws Exception {
        Reply received = replies.saveAndFlush(Reply.text(newWorry(), senior, "받은 답장"));
        thanksRepository.saveAndFlush(Thanks.of(received, ThanksStamp.HELPED, null));

        updateContent(senior, reply.getId(), "고친 답장").andExpect(status().isOk());
    }

    // ---------- 내용 검사 422 ----------

    @Test
    void 금지어_전화번호_링크가_있으면_422이고_바뀌지_않는다() throws Exception {
        updateContent(senior, reply.getId(), "씨발 힘내")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILTER_422_001"));
        updateContent(senior, reply.getId(), "연락은 010-1234-5678 로 주세요")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILTER_422_002"));
        updateContent(senior, reply.getId(), "여기 https://example.com 봐요")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILTER_422_003"));

        assertThat(reload(reply.getId()).getContent()).isEqualTo("처음 답장");
    }

    @Test
    void 말_답장도_같은_검사를_받는다() throws Exception {
        Reply voice = replies.saveAndFlush(Reply.voice(newWorry(), senior, "말로 한 답장"));

        updateContent(senior, voice.getId(), "카톡 아이디 알려줄게")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILTER_422_003"));
    }
}
