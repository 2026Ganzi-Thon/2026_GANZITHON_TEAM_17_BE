package com.ganzithon.wool.report.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.report.entity.ReplyReportReason;
import com.ganzithon.wool.report.repository.ReplyReportRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REPORT-001 답장 신고
@AutoConfigureMockMvc
@Transactional
class ReplyReportControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private ReplyReportRepository reports;
    @Autowired private NotificationRepository notifications;
    @Autowired private EntityManager entityManager;

    private User senior;
    private User youth;
    private Reply reply;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    // 청년이 쓴 고민에 senior가 답장한 상태를 만든다.
    private Reply replyFrom(User author, User by) {
        Worry worry = worries.saveAndFlush(Worry.of(author, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        return replies.saveAndFlush(Reply.text(worry, by, "답장"));
    }

    private ResultActions report(User who, Long replyId, String body) throws Exception {
        var request = post("/api/replies/{replyId}/reports", replyId).contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    private ResultActions report(User who, Reply target, ReplyReportReason reason) throws Exception {
        return report(who, target.getId(), "{\"reason\":\"" + reason + "\"}");
    }

    // 서로 다른 청년이 senior의 서로 다른 답장을 신고한다.
    private void reportByNewYouth() throws Exception {
        User other = newUser(Role.YOUTH, "신고청년");
        report(other, replyFrom(other, senior), ReplyReportReason.ABUSE).andExpect(status().isCreated());
    }

    private User reloadSenior() {
        entityManager.flush();
        entityManager.clear();
        return users.findById(senior.getId()).orElseThrow();
    }

    private List<Notification> sanctionNotifications() {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()).stream()
                .filter(n -> n.getType() == NotificationType.SANCTION).toList();
    }

    @BeforeEach
    void setUp() {
        senior = newUser(Role.SENIOR, "선배");
        youth = newUser(Role.YOUTH, "청년");
        reply = replyFrom(youth, senior);
    }

    // ---------- 신고와 숨김 ----------

    @Test
    void 신고하면_201과_래퍼로_알려주고_답장을_숨기고_신고를_저장한다() throws Exception {
        report(youth, reply, ReplyReportReason.SEXUAL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("REPORT_201_1"))
                .andExpect(jsonPath("$.message").value("신고가 접수되었고 답장을 숨겼습니다."))
                .andExpect(jsonPath("$.data.replyId").value(reply.getId()))
                .andExpect(jsonPath("$.data.hidden").value(true));

        entityManager.flush();
        entityManager.clear();
        assertThat(replies.findById(reply.getId()).orElseThrow().isHidden()).isTrue();
        assertThat(reports.existsByReplyIdAndReporterId(reply.getId(), youth.getId())).isTrue();
    }

    @Test
    void 사유는_다섯_가지_모두_받는다() throws Exception {
        for (ReplyReportReason reason : ReplyReportReason.values()) {
            User other = newUser(Role.YOUTH, "사유청년");
            report(other, replyFrom(other, newUser(Role.SENIOR, "사유선배")), reason).andExpect(status().isCreated());
        }
    }

    @Test
    void 숨긴_답장은_누적_답장_수와_어르신_목록의_답장_수에서_빠진다() throws Exception {
        replyFrom(newUser(Role.YOUTH, "다른청년"), senior); // 다른 고민의 답장 하나
        assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(2);

        report(youth, reply, ReplyReportReason.AD).andExpect(status().isCreated());

        assertThat(replies.countVisibleBySeniorId(senior.getId())).isEqualTo(1);
        mockMvc.perform(get("/api/senior/wool").header("X-User-Id", senior.getId()))
                .andExpect(jsonPath("$.data.totalReplies").value(1));
    }

    // ---------- 실패 ----------

    @Test
    void 같은_답장을_다시_신고하면_409다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());

        report(youth, reply, ReplyReportReason.AD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_409_001"))
                .andExpect(jsonPath("$.message").value("이미 신고한 답장입니다."));
        assertThat(reports.count()).isEqualTo(1);
    }

    @Test
    void 내_고민의_답장이_아니면_403이고_신고도_숨김도_없다() throws Exception {
        User stranger = newUser(Role.YOUTH, "남의청년");

        report(stranger, reply, ReplyReportReason.ABUSE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLY_403_001"))
                .andExpect(jsonPath("$.message").value("볼 수 없는 답장입니다."));

        entityManager.flush();
        entityManager.clear();
        assertThat(replies.findById(reply.getId()).orElseThrow().isHidden()).isFalse();
        assertThat(reports.count()).isZero();
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
    }

    @Test
    void 없거나_삭제된_답장은_404다() throws Exception {
        report(youth, 999_999L, "{\"reason\":\"ABUSE\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));

        mockMvc.perform(delete("/api/replies/{replyId}", reply.getId()).header("X-User-Id", senior.getId()))
                .andExpect(status().isOk());
        report(youth, reply, ReplyReportReason.ABUSE)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));
    }

    @Test
    void 사유가_없거나_잘못되면_400이다() throws Exception {
        for (String body : new String[]{"{}", "{\"reason\":null}", "{\"reason\":\"HATE\"}", "{\"reason\":1}", "{\"reason\":\"abuse\"}", ""}) {
            report(youth, reply.getId(), body).andExpect(status().isBadRequest());
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(replies.findById(reply.getId()).orElseThrow().isHidden()).isFalse();
        assertThat(reports.count()).isZero();
    }

    @Test
    void 헤더가_없으면_401_어르신이면_403이다() throws Exception {
        report(null, reply, ReplyReportReason.ABUSE)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        report(senior, reply, ReplyReportReason.ABUSE)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }

    // ---------- 제재 ----------

    @Test
    void 첫_신고에서_경고가_적용되고_알림이_간다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());

        User after = reloadSenior();
        assertThat(after.getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
        assertThat(after.getRestrictedUntil()).isNull();
        List<Notification> sent = sanctionNotifications();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getTargetKind()).isEqualTo(TargetKind.SANCTION);
        assertThat(sent.get(0).getTargetId()).isNull();
        assertThat(sent.get(0).getTitle()).isEqualTo("답장에 대한 경고가 있어요");
    }

    @Test
    void 신고한_청년이_서로_다르게_셋이면_7일_답장_제한이고_다섯이면_영구_제한이다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated()); // 1명: 경고
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);

        reportByNewYouth(); // 2명: 그대로
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
        assertThat(sanctionNotifications()).hasSize(1);

        LocalDateTime before = LocalDateTime.now();
        reportByNewYouth(); // 3명: 7일 제한
        User restricted = reloadSenior();
        assertThat(restricted.getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
        assertThat(Duration.between(before.plusDays(7), restricted.getRestrictedUntil()).abs()).isLessThan(Duration.ofMinutes(1));
        assertThat(sanctionNotifications()).hasSize(2);
        assertThat(sanctionNotifications().get(0).getTitle()).isEqualTo("7일 동안 답장을 쓸 수 없어요");

        reportByNewYouth(); // 4명: 그대로
        User stillRestricted = reloadSenior();
        assertThat(stillRestricted.getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
        assertThat(stillRestricted.getRestrictedUntil()).isEqualTo(restricted.getRestrictedUntil());
        assertThat(sanctionNotifications()).hasSize(2);

        reportByNewYouth(); // 5명: 영구 제한
        User banned = reloadSenior();
        assertThat(banned.getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
        assertThat(banned.getRestrictedUntil()).isNull();
        assertThat(sanctionNotifications()).hasSize(3);
        assertThat(sanctionNotifications().get(0).getTitle()).isEqualTo("더 이상 답장을 쓸 수 없어요");
    }

    // 7일 제한이 풀린 뒤에도 누적 수는 그대로다. 같은 청년의 추가 신고나 4번째 신고로 다시 제한하지 않는다.
    @Test
    void 제한이_풀린_뒤에는_같은_청년의_추가_신고나_네_번째_신고로_다시_제한하지_않는다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
        reportByNewYouth();
        reportByNewYouth(); // 3명: 7일 제한
        User restricted = reloadSenior();
        restricted.liftRestriction();
        users.saveAndFlush(restricted);
        assertThat(sanctionNotifications()).hasSize(2);

        report(youth, replyFrom(youth, senior), ReplyReportReason.AD).andExpect(status().isCreated()); // 같은 청년, 누적 3 그대로
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);

        reportByNewYouth(); // 4명
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(4);
        assertThat(sanctionNotifications()).hasSize(2);
    }

    // 이미 더 높은 단계(영구 제한)인 어르신은 낮은 단계 기준에 도달해도 단계가 내려가거나 알림이 가지 않는다.
    @Test
    void 이미_영구_제한인_어르신은_낮은_단계_기준에_도달해도_내려가지_않는다() throws Exception {
        senior.applySanction(SanctionLevel.BANNED, null);
        users.saveAndFlush(senior);

        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
        reportByNewYouth();
        reportByNewYouth(); // 누적 3명: 7일 제한 기준에 도달

        User after = reloadSenior();
        assertThat(after.getSanctionLevel()).isEqualTo(SanctionLevel.BANNED);
        assertThat(after.getRestrictedUntil()).isNull();
        assertThat(sanctionNotifications()).isEmpty();
    }

    @Test
    void 같은_청년이_같은_어르신의_다른_답장을_또_신고해도_누적_신고_수는_늘지_않는다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
        Reply second = replyFrom(youth, senior);
        Reply third = replyFrom(youth, senior);

        report(youth, second, ReplyReportReason.AD).andExpect(status().isCreated());
        report(youth, third, ReplyReportReason.ETC).andExpect(status().isCreated());

        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(1);
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
        assertThat(sanctionNotifications()).hasSize(1);
        // 답장은 세 통 모두 숨겨진다.
        assertThat(replies.countVisibleBySeniorId(senior.getId())).isZero();
    }

    @Test
    void 어르신이_삭제한_답장의_신고도_누적_수에_계속_센다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated()); // 1명
        mockMvc.perform(delete("/api/replies/{replyId}", reply.getId()).header("X-User-Id", senior.getId()))
                .andExpect(status().isOk());
        reportByNewYouth(); // 2명
        reportByNewYouth(); // 3명

        assertThat(reports.countDistinctReportersBySeniorId(senior.getId())).isEqualTo(3);
        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
    }

    @Test
    void 다른_어르신의_신고는_섞이지_않는다() throws Exception {
        User other = newUser(Role.SENIOR, "다른선배");
        for (int i = 0; i < 3; i++) {
            User reporter = newUser(Role.YOUTH, "남청년");
            report(reporter, replyFrom(reporter, other), ReplyReportReason.ABUSE).andExpect(status().isCreated());
        }

        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());

        assertThat(reloadSenior().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
        assertThat(users.findById(other.getId()).orElseThrow().getSanctionLevel()).isEqualTo(SanctionLevel.RESTRICTED);
    }

    @Test
    void 제한된_어르신은_답장을_쓸_수_없다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
        reportByNewYouth();
        reportByNewYouth(); // 3명: 7일 제한
        Worry target = worries.saveAndFlush(Worry.of(youth, "새 고민", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));

        mockMvc.perform(post("/api/worries/{worryId}/replies", target.getId())
                        .header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"답장\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLY_403_002"))
                .andExpect(jsonPath("$.data.restrictedUntil").exists());
    }

    @Test
    void 경고_단계는_답장을_막지_않는다() throws Exception {
        report(youth, reply, ReplyReportReason.ABUSE).andExpect(status().isCreated());
        Worry target = worries.saveAndFlush(Worry.of(youth, "새 고민", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));

        mockMvc.perform(post("/api/worries/{worryId}/replies", target.getId())
                        .header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"답장\"}"))
                .andExpect(status().isCreated());
    }
}
