package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.certificate.entity.Certificate;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
import com.ganzithon.wool.report.entity.ReplyReport;
import com.ganzithon.wool.report.entity.ReplyReportReason;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// REPLY-007 답장 삭제
@AutoConfigureMockMvc
@Transactional
class ReplyDeleteControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private CertificateRepository certificates;
    @Autowired private EntityManager entityManager;

    private User youth;
    private User senior;
    private User other;
    private Worry worry;
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

    private Reply seed(Worry target, User by, boolean hidden, boolean deleted) {
        Reply seeded = Reply.text(target, by, "답장");
        if (hidden) seeded.hide();
        if (deleted) seeded.delete();
        return replies.saveAndFlush(seeded);
    }

    private ResultActions remove(User who, Long replyId) throws Exception {
        var request = delete("/api/replies/{replyId}", replyId);
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
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
        worry = newWorry();
        reply = seed(worry, senior, false, false);
    }

    @Test
    void 삭제하면_200과_래퍼로_삭제한_답장과_삭제_후_누적_수를_준다() throws Exception {
        seed(newWorry(), senior, false, false);
        seed(newWorry(), senior, true, false);
        seed(newWorry(), senior, false, true);
        seed(newWorry(), other, false, false);

        remove(senior, reply.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("REPLY_200_7"))
                .andExpect(jsonPath("$.message").value("답장을 삭제했습니다."))
                .andExpect(jsonPath("$.data.replyId").value(reply.getId()))
                .andExpect(jsonPath("$.data.totalReplies").value(1));

        assertThat(reload(reply.getId()).isDeleted()).isTrue();
    }

    @Test
    void 이미_삭제한_답장을_다시_삭제하면_404다() throws Exception {
        remove(senior, reply.getId()).andExpect(status().isOk());

        remove(senior, reply.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPLY_404_001"))
                .andExpect(jsonPath("$.message").value("답장을 찾을 수 없습니다."));
    }

    @Test
    void 없는_답장은_404다() throws Exception {
        remove(senior, 999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLY_404_001"));
    }

    @Test
    void 다른_어르신의_답장은_403이고_삭제되지_않는다() throws Exception {
        remove(other, reply.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPLY_403_003"))
                .andExpect(jsonPath("$.message").value("삭제할 권한이 없습니다."));

        assertThat(reload(reply.getId()).isDeleted()).isFalse();
    }

    @Test
    void 헤더가_없으면_401_청년이면_403이다() throws Exception {
        remove(null, reply.getId()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        remove(youth, reply.getId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));

        assertThat(reload(reply.getId()).isDeleted()).isFalse();
    }

    @Test
    void 신고로_숨겨진_답장도_작성자는_삭제할_수_있고_누적_수는_그대로다() throws Exception {
        Reply hidden = seed(newWorry(), senior, true, false);

        remove(senior, hidden.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReplies").value(1));

        Reply after = reload(hidden.getId());
        assertThat(after.isDeleted()).isTrue();
        assertThat(after.isHidden()).isTrue();
    }

    @Test
    void 삭제하면_고민의_유효_답장이_사라져_다시_0통이_되고_어르신_목록에도_반영된다() throws Exception {
        assertThat(replies.countVisibleByWorryId(worry.getId())).isEqualTo(1);

        remove(senior, reply.getId()).andExpect(status().isOk());

        assertThat(replies.countVisibleByWorryId(worry.getId())).isZero();
        assertThat(replies.existsVisibleByWorryId(worry.getId())).isFalse();
        mockMvc.perform(get("/api/senior/worries").header("X-User-Id", other.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.worryId==" + worry.getId() + ")].replyCount").value(0));
    }

    @Test
    void 삭제해도_같은_고민에_다시_답장할_수_없다() throws Exception {
        remove(senior, reply.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/api/worries/{worryId}/replies", worry.getId())
                        .header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"다시\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLY_409_001"));
    }

    @Test
    void 이미_발급한_감사장은_삭제해도_회수하지_않는다() throws Exception {
        Certificate certificate = certificates.saveAndFlush(Certificate.issue(senior, 20, LocalDateTime.now()));

        remove(senior, reply.getId()).andExpect(status().isOk());

        assertThat(certificates.existsById(certificate.getId())).isTrue();
        assertThat(certificates.existsBySeniorIdAndReplyCount(senior.getId(), 20)).isTrue();
    }

    @Test
    void 신고_이력과_적용된_제재는_삭제해도_그대로다() throws Exception {
        senior.applySanction(SanctionLevel.WARNED, null);
        users.saveAndFlush(senior);
        entityManager.persist(ReplyReport.of(reply, youth, ReplyReportReason.AD));
        entityManager.flush();

        remove(senior, reply.getId()).andExpect(status().isOk());

        entityManager.clear();
        Long reports = entityManager.createQuery("select count(rr) from ReplyReport rr where rr.reply.id = :id", Long.class)
                .setParameter("id", reply.getId()).getSingleResult();
        assertThat(reports).isEqualTo(1);
        assertThat(users.findById(senior.getId()).orElseThrow().getSanctionLevel()).isEqualTo(SanctionLevel.WARNED);
    }
}
