package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.certificate.entity.Certificate;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.reply.service.ReplyCreateService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class ReplyCreateControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private CertificateRepository certificates;
    @Autowired private NotificationRepository notifications;
    @Autowired private EntityManager entityManager;
    @Autowired private ReplyCreateService replyCreateService;

    private User youth;
    private User senior;
    private Worry worry;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private Worry newWorry(User author) {
        return worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private Reply seed(Worry target, User by, boolean hidden, boolean deleted) {
        Reply reply = Reply.text(target, by, "이전 답장");
        if (hidden) reply.hide();
        if (deleted) reply.delete();
        return replies.saveAndFlush(reply);
    }

    // 어르신이 이미 count통을 보낸 상태를 만든다. (고민마다 하나씩)
    private void seedReplies(User by, int count) {
        for (int i = 0; i < count; i++) {
            seed(newWorry(youth), by, false, false);
        }
    }

    private ResultActions send(User sender, Long worryId, String body) throws Exception {
        var request = post("/api/worries/{worryId}/replies", worryId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(sender == null ? request : request.header("X-User-Id", sender.getId()));
    }

    private static String text(String content) {
        return "{\"type\":\"TEXT\",\"content\":\"" + content + "\"}";
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "선배");
        worry = newWorry(youth);
    }

    // ---------- 성공 ----------

    @Test
    void 글_답장을_보내면_201과_래퍼로_결과를_내려주고_원문_그대로_저장한다() throws Exception {
        send(senior, worry.getId(), text("  앞뒤 공백도  그대로  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("REPLY_201_1"))
                .andExpect(jsonPath("$.message").value("선배님의 털 한 올이 전해졌어요."))
                .andExpect(jsonPath("$.data.totalReplies").value(1))
                .andExpect(jsonPath("$.data.woolStage").value("BALL"))
                .andExpect(jsonPath("$.data.certificateIssued").value(false))
                .andExpect(jsonPath("$.data.certificateId").doesNotExist())
                .andExpect(jsonPath("$.data.replyId").isNumber());

        entityManager.clear();
        List<Reply> saved = replies.findAll().stream().filter(r -> r.getWorry().getId().equals(worry.getId())).toList();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo(ReplyType.TEXT);
        assertThat(saved.get(0).getContent()).isEqualTo("  앞뒤 공백도  그대로  ");
        assertThat(saved.get(0).getSenior().getId()).isEqualTo(senior.getId());
        assertThat(saved.get(0).getImageUrl()).isNull();
    }

    @Test
    void 말_답장은_VOICE로_저장된다() throws Exception {
        send(senior, worry.getId(), "{\"type\":\"VOICE\",\"content\":\"말로 한 답장\",\"imageUrl\":null}")
                .andExpect(status().isCreated());

        entityManager.clear();
        assertThat(replies.findAll().stream().filter(r -> r.getWorry().getId().equals(worry.getId())).findFirst().orElseThrow()
                .getType()).isEqualTo(ReplyType.VOICE);
    }

    @Test
    void 손편지_답장은_이미지_주소만_저장하고_본문은_없다() throws Exception {
        send(senior, worry.getId(), "{\"type\":\"LETTER\",\"imageUrl\":\"https://example.com/letter.png\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(1));

        entityManager.clear();
        Reply saved = replies.findAll().stream().filter(r -> r.getWorry().getId().equals(worry.getId())).findFirst().orElseThrow();
        assertThat(saved.getType()).isEqualTo(ReplyType.LETTER);
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/letter.png");
    }

    @Test
    void 전체_공개가_아닌_고민에도_어르신은_답장할_수_있다() throws Exception {
        Worry seniorsOnly = worries.saveAndFlush(Worry.of(youth, "비공개", "본문", WorryTag.LOVE, WorryVisibility.SENIORS_ONLY));

        send(senior, seniorsOnly.getId(), text("답장")).andExpect(status().isCreated());
    }

    @Test
    void 손편지에는_금지어_검사를_하지_않는다() throws Exception {
        // 이미지 주소는 사람이 쓴 글이 아니라 업로드 결과다. 검사 대상은 글·말 답장의 content뿐이다.
        send(senior, worry.getId(), "{\"type\":\"LETTER\",\"imageUrl\":\"https://example.com/x.png\"}")
                .andExpect(status().isCreated());
    }

    // ---------- 알림 ----------

    @Test
    void 답장이_도착하면_고민_작성자에게_알림이_간다() throws Exception {
        send(senior, worry.getId(), text("답장")).andExpect(status().isCreated());

        List<Notification> list = notifications.findByUserIdOrderByCreatedAtDescIdDesc(youth.getId());
        assertThat(list).hasSize(1);
        Notification notification = list.get(0);
        assertThat(notification.getType()).isEqualTo(NotificationType.REPLY_ARRIVED);
        assertThat(notification.getTitle()).isEqualTo(senior.getNickname() + " 선배님이 답장을 보냈어요");
        assertThat(notification.getTargetKind()).isEqualTo(TargetKind.REPLY);
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId())).isEmpty();
    }

    @Test
    void 알림의_대상은_새로_만든_답장이다() throws Exception {
        send(senior, worry.getId(), text("답장")).andExpect(status().isCreated());

        entityManager.clear();
        Long replyId = replies.findAll().stream().filter(r -> r.getWorry().getId().equals(worry.getId()))
                .findFirst().orElseThrow().getId();
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(youth.getId()).get(0).getTargetId()).isEqualTo(replyId);
    }

    // ---------- 털 단계와 감사장 ----------

    @Test
    void 누적_답장_수는_숨김과_삭제를_제외하고_센다() throws Exception {
        seed(newWorry(youth), senior, false, false);
        seed(newWorry(youth), senior, true, false);
        seed(newWorry(youth), senior, false, true);

        send(senior, worry.getId(), text("답장"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(2));
    }

    @Test
    void 털_단계는_5통까지_BALL_6통부터_SCARF이다() throws Exception {
        seedReplies(senior, 4);
        send(senior, worry.getId(), text("5번째"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(5))
                .andExpect(jsonPath("$.data.woolStage").value("BALL"));

        Worry next = newWorry(youth);
        send(senior, next.getId(), text("6번째"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(6))
                .andExpect(jsonPath("$.data.woolStage").value("SCARF"));
    }

    @Test
    void 열아홉_통이면_감사장이_없고_스무_번째_답장에서_감사장을_발급한다() throws Exception {
        seedReplies(senior, 18);
        send(senior, worry.getId(), text("19번째"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(19))
                .andExpect(jsonPath("$.data.woolStage").value("SCARF"))
                .andExpect(jsonPath("$.data.certificateIssued").value(false))
                .andExpect(jsonPath("$.data.certificateId").doesNotExist());
        assertThat(certificates.count()).isZero();

        Worry twentieth = newWorry(youth);
        send(senior, twentieth.getId(), text("20번째"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(20))
                .andExpect(jsonPath("$.data.woolStage").value("SWEATER"))
                .andExpect(jsonPath("$.data.certificateIssued").value(true))
                .andExpect(jsonPath("$.data.certificateId").isNumber());

        entityManager.clear();
        List<Certificate> issued = certificates.findAll();
        assertThat(issued).hasSize(1);
        assertThat(issued.get(0).getReplyCount()).isEqualTo(20);
        assertThat(issued.get(0).getSenior().getId()).isEqualTo(senior.getId());

        Notification notification = notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).get(0);
        assertThat(notification.getType()).isEqualTo(NotificationType.CERTIFICATE_ISSUED);
        assertThat(notification.getTargetKind()).isEqualTo(TargetKind.CERTIFICATE);
        assertThat(notification.getTargetId()).isEqualTo(issued.get(0).getId());
        assertThat(notification.getContent()).isEqualTo("20통의 답장을 전해 주셔서 고맙습니다.");
    }

    @Test
    void 마흔_통째에는_두_번째_감사장을_발급한다() throws Exception {
        certificates.saveAndFlush(Certificate.issue(senior, 20, LocalDateTime.now().minusDays(3)));
        seedReplies(senior, 39);

        send(senior, worry.getId(), text("40번째"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(40))
                .andExpect(jsonPath("$.data.certificateIssued").value(true));
        assertThat(certificates.count()).isEqualTo(2);
    }

    @Test
    void 숨김이나_삭제로_줄었다가_다시_스무_통이_되어도_감사장을_다시_만들지_않는다() throws Exception {
        seedReplies(senior, 19);
        Reply hidden = seed(newWorry(youth), senior, false, false);
        certificates.saveAndFlush(Certificate.issue(senior, 20, LocalDateTime.now().minusDays(1)));
        hidden.hide();
        replies.saveAndFlush(hidden);

        send(senior, worry.getId(), text("다시 20번째"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(20))
                .andExpect(jsonPath("$.data.certificateIssued").value(false))
                .andExpect(jsonPath("$.data.certificateId").doesNotExist());
        assertThat(certificates.count()).isEqualTo(1);
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId())).isEmpty();
    }

    // 요청 시작 때 읽은 사용자(@LoginUser)가 오래된 값이어도, 잠금 뒤에 다시 읽은 최신 제재 상태로 판단해야 한다.
    @Test
    void 요청_도중_제재가_걸렸으면_읽어_둔_사용자_값과_상관없이_막는다() {
        assertThat(senior.getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        entityManager.createNativeQuery("UPDATE users SET sanction_level = 'BANNED' WHERE id = :id")
                .setParameter("id", senior.getId()).executeUpdate();
        // 영속성 컨텍스트의 senior는 아직 NONE이다. (DB만 바뀐 상태)
        assertThat(senior.getSanctionLevel()).isEqualTo(SanctionLevel.NONE);

        assertThatThrownBy(() -> replyCreateService.create(senior, worry.getId(),
                new CreateReplyRequest(ReplyType.TEXT, "답장", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ReplyErrorCode.RESTRICTED));
        assertThat(replies.countVisibleByWorryId(worry.getId())).isZero();
    }

    @Test
    void 다른_어르신의_답장은_내_누적_수에_섞이지_않는다() throws Exception {
        User other = newUser(Role.SENIOR, "다른선배");
        seedReplies(other, 3);

        send(senior, worry.getId(), text("내 첫 답장"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalReplies").value(1));
    }

    // ---------- 인증·권한·대상 ----------

    @Test
    void 헤더가_없거나_없는_사용자는_401이다() throws Exception {
        send(null, worry.getId(), text("답장")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/worries/{worryId}/replies", worry.getId()).header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(text("답장")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    @Test
    void 청년은_답장할_수_없다() throws Exception {
        send(youth, worry.getId(), text("답장"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_403_001"));
        assertThat(replies.countVisibleByWorryId(worry.getId())).isZero();
    }

    @Test
    void 없는_삭제한_숨김_고민에는_404이다() throws Exception {
        send(senior, Long.MAX_VALUE, text("답장"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));

        Worry deleted = newWorry(youth);
        deleted.delete();
        worries.saveAndFlush(deleted);
        send(senior, deleted.getId(), text("답장"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));

        Worry hidden = newWorry(youth);
        entityManager.createNativeQuery("UPDATE worries SET hidden = TRUE WHERE id = :id")
                .setParameter("id", hidden.getId()).executeUpdate();
        entityManager.clear();
        send(senior, hidden.getId(), text("답장"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
    }

    // ---------- 같은 고민에 한 번만 ----------

    @Test
    void 같은_고민에_두_번_답장하면_409이고_하나만_남는다() throws Exception {
        send(senior, worry.getId(), text("첫 답장")).andExpect(status().isCreated());

        send(senior, worry.getId(), text("두 번째 답장"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("REPLY_409_001"))
                .andExpect(jsonPath("$.message").value("이미 답장한 고민이에요."));
        assertThat(replies.countVisibleByWorryId(worry.getId())).isEqualTo(1);
    }

    @Test
    void 삭제하거나_숨김당한_답장이_있어도_같은_고민에_다시_답장할_수_없다() throws Exception {
        Reply deleted = seed(worry, senior, false, true);
        send(senior, worry.getId(), text("삭제 후 재답장"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPLY_409_001"));

        Worry other = newWorry(youth);
        seed(other, senior, true, false);
        send(senior, other.getId(), text("숨김 후 재답장"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPLY_409_001"));
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    void 다른_어르신은_같은_고민에_답장할_수_있다() throws Exception {
        User other = newUser(Role.SENIOR, "다른선배");
        seed(worry, other, false, false);

        send(senior, worry.getId(), text("나도 답장"))
                .andExpect(status().isCreated());
        assertThat(replies.countVisibleByWorryId(worry.getId())).isEqualTo(2);
    }

    // ---------- 제재 ----------

    @Test
    void 영구_제한이면_403이고_restrictedUntil은_null이다() throws Exception {
        senior.applySanction(SanctionLevel.BANNED, null);
        users.saveAndFlush(senior);

        send(senior, worry.getId(), text("답장"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLY_403_002"))
                .andExpect(jsonPath("$.message").value("지금은 답장할 수 없어요."))
                .andExpect(jsonPath("$.data.restrictedUntil").value((Object) null));
        assertThat(replies.countVisibleByWorryId(worry.getId())).isZero();
    }

    @Test
    void 일주일_제한_중이면_403이고_해제_시각을_알려준다() throws Exception {
        senior.applySanction(SanctionLevel.RESTRICTED, LocalDateTime.now().plusDays(3));
        users.saveAndFlush(senior);

        send(senior, worry.getId(), text("답장"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLY_403_002"))
                .andExpect(jsonPath("$.data.restrictedUntil").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void 제한_시각이_지났거나_경고만_받았으면_답장할_수_있다() throws Exception {
        senior.applySanction(SanctionLevel.RESTRICTED, LocalDateTime.now().minusMinutes(1));
        users.saveAndFlush(senior);
        send(senior, worry.getId(), text("제한이 끝난 뒤")).andExpect(status().isCreated());

        User warned = newUser(Role.SENIOR, "경고선배");
        warned.applySanction(SanctionLevel.WARNED, null);
        users.saveAndFlush(warned);
        send(warned, worry.getId(), text("경고만 받음")).andExpect(status().isCreated());
    }

    // ---------- 입력 검증 400 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"content\":\"내용\"}",
            "{\"type\":\"UNKNOWN\",\"content\":\"내용\"}",
            "{\"type\":null,\"content\":\"내용\"}",
            "{\"type\":\"TEXT\"}",
            "{\"type\":\"TEXT\",\"content\":null}",
            "{\"type\":\"TEXT\",\"content\":\"\"}",
            "{\"type\":\"TEXT\",\"content\":\"   \"}",
            "{\"type\":\"TEXT\",\"content\":\"\\n\\t \"}",
            "{\"type\":\"VOICE\"}",
            "{\"type\":\"VOICE\",\"content\":\" \"}",
            "{\"type\":\"TEXT\",\"content\":123}",
            "{\"type\":\"TEXT\",\"content\":true}",
            "{\"type\":\"TEXT\",\"content\":\"내용\",\"imageUrl\":\"https://example.com/a.png\"}",
            "{\"type\":\"VOICE\",\"content\":\"내용\",\"imageUrl\":\"\"}",
            "{\"type\":\"LETTER\"}",
            "{\"type\":\"LETTER\",\"imageUrl\":null}",
            "{\"type\":\"LETTER\",\"imageUrl\":\"\"}",
            "{\"type\":\"LETTER\",\"imageUrl\":\"   \"}",
            "{\"type\":\"LETTER\",\"imageUrl\":123}",
            "{\"type\":\"LETTER\",\"imageUrl\":\"https://example.com/a.png\",\"content\":\"내용\"}",
            "{\"type\":\"LETTER\",\"content\":\"내용\"}",
            "not json",
            ""
    })
    void 형식이_맞지_않으면_400이고_저장하지_않는다(String body) throws Exception {
        send(senior, worry.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        assertThat(replies.countVisibleByWorryId(worry.getId())).isZero();
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(youth.getId())).isEmpty();
    }

    @Test
    void 천_자는_허용하고_천한_자는_400이다() throws Exception {
        send(senior, worry.getId(), text("가".repeat(1000))).andExpect(status().isCreated());

        Worry other = newWorry(youth);
        send(senior, other.getId(), text("가".repeat(1001))).andExpect(status().isBadRequest());
    }

    @Test
    void 이모지는_한_글자로_세어_천_개까지_허용한다() throws Exception {
        send(senior, worry.getId(), text("😀".repeat(1000))).andExpect(status().isCreated());

        Worry other = newWorry(youth);
        send(senior, other.getId(), text("😀".repeat(1001))).andExpect(status().isBadRequest());
    }

    @Test
    void 손편지_이미지_주소는_500자까지_허용한다() throws Exception {
        send(senior, worry.getId(), "{\"type\":\"LETTER\",\"imageUrl\":\"" + "a".repeat(500) + "\"}")
                .andExpect(status().isCreated());

        Worry other = newWorry(youth);
        send(senior, other.getId(), "{\"type\":\"LETTER\",\"imageUrl\":\"" + "a".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- 내용 검사 422 ----------

    @Test
    void 금지어가_있으면_422이고_저장하지_않는다() throws Exception {
        send(senior, worry.getId(), text("씨발 힘내"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILTER_422_001"));
        assertNothingSaved();
    }

    @Test
    void 전화번호가_있으면_422이다() throws Exception {
        send(senior, worry.getId(), text("연락은 010-1234-5678 로 주세요"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILTER_422_002"));
        assertNothingSaved();
    }

    @Test
    void 링크나_외부_연락처가_있으면_422이다() throws Exception {
        send(senior, worry.getId(), text("여기 https://example.com 봐요"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILTER_422_003"));
        assertNothingSaved();
    }

    @Test
    void 말_답장도_같은_검사를_받는다() throws Exception {
        send(senior, worry.getId(), "{\"type\":\"VOICE\",\"content\":\"카톡 아이디 알려줄게\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILTER_422_003"));
        assertNothingSaved();
    }

    @Test
    void 막힌_내용은_답장_수와_알림과_감사장에_영향을_주지_않는다() throws Exception {
        seedReplies(senior, 19);
        send(senior, worry.getId(), text("씨발")).andExpect(status().isUnprocessableEntity());

        assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(19);
        assertThat(certificates.count()).isZero();
    }

    private void assertNothingSaved() {
        assertThat(replies.countVisibleByWorryId(worry.getId())).isZero();
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(youth.getId())).isEmpty();
    }
}
