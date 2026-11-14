package com.ganzithon.wool.report.repository;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.report.entity.ReplyReport;
import com.ganzithon.wool.report.entity.ReplyReportReason;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ReplyReportRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ReplyReportRepository replyReportRepository;

    private User youth;
    private Reply reply;

    @BeforeEach
    void setUp() {
        youth = User.join(Role.YOUTH, AgeGroup.AGE_20S, "포근한 양 1");
        User senior = User.join(Role.SENIOR, AgeGroup.AGE_60S, "다정한 양 2");
        entityManager.persist(youth);
        entityManager.persist(senior);
        Worry worry = Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC);
        entityManager.persist(worry);
        reply = Reply.text(worry, senior, "내용");
        entityManager.persist(reply);
    }

    @Test
    void 신고를_저장하고_다시_조회하면_답장_신고자_사유가_유지된다() {
        ReplyReport report = replyReportRepository.saveAndFlush(ReplyReport.of(reply, youth, ReplyReportReason.AD));
        entityManager.clear();

        ReplyReport saved = replyReportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getReply().getId()).isEqualTo(reply.getId());
        assertThat(saved.getReporter().getId()).isEqualTo(youth.getId());
        assertThat(saved.getReason()).isEqualTo(ReplyReportReason.AD);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_청년이_같은_답장을_두_번_신고하면_저장이_거부된다() {
        replyReportRepository.saveAndFlush(ReplyReport.of(reply, youth, ReplyReportReason.ABUSE));

        assertThatThrownBy(() -> replyReportRepository.saveAndFlush(ReplyReport.of(reply, youth, ReplyReportReason.AD)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 신고_여부는_답장과_신고자_조합으로_확인한다() {
        User other = User.join(Role.YOUTH, AgeGroup.AGE_30S, "포근한 양 9");
        entityManager.persist(other);
        replyReportRepository.saveAndFlush(ReplyReport.of(reply, youth, ReplyReportReason.ETC));

        assertThat(replyReportRepository.existsByReplyIdAndReporterId(reply.getId(), youth.getId())).isTrue();
        assertThat(replyReportRepository.existsByReplyIdAndReporterId(reply.getId(), other.getId())).isFalse();
    }

    @Test
    void 필수_값이_없으면_거부한다() {
        assertThatThrownBy(() -> ReplyReport.of(null, youth, ReplyReportReason.AD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReplyReport.of(reply, null, ReplyReportReason.AD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReplyReport.of(reply, youth, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
