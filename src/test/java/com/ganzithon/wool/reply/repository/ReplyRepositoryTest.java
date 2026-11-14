package com.ganzithon.wool.reply.repository;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
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
class ReplyRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ReplyRepository replyRepository;

    private User youth;
    private Worry worry;

    @BeforeEach
    void setUp() {
        youth = persistUser(Role.YOUTH, "포근한 양 1");
        worry = Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC);
        entityManager.persist(worry);
    }

    @Test
    void 답장을_저장하고_다시_조회하면_고민_어르신_종류_원문이_유지된다() {
        User senior = persistUser(Role.SENIOR, "다정한 양 2");
        Reply reply = replyRepository.saveAndFlush(Reply.text(worry, senior, "  원문 그대로  "));
        Long id = reply.getId();
        entityManager.clear();

        Reply saved = replyRepository.findById(id).orElseThrow();
        assertThat(saved.getWorry().getId()).isEqualTo(worry.getId());
        assertThat(saved.getSenior().getId()).isEqualTo(senior.getId());
        assertThat(saved.getType()).isEqualTo(ReplyType.TEXT);
        assertThat(saved.getContent()).isEqualTo("  원문 그대로  ");
        assertThat(saved.isHidden()).isFalse();
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getFirstViewedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 손편지_답장은_본문_없이_저장된다() {
        Reply reply = replyRepository.saveAndFlush(Reply.letter(worry, persistUser(Role.SENIOR, "다정한 양 2"), "https://x/y.png"));
        entityManager.clear();

        Reply saved = replyRepository.findById(reply.getId()).orElseThrow();
        assertThat(saved.getType()).isEqualTo(ReplyType.LETTER);
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getImageUrl()).isEqualTo("https://x/y.png");
    }

    @Test
    void 같은_어르신이_같은_고민에_두_번_답장하면_저장이_거부된다() {
        User senior = persistUser(Role.SENIOR, "다정한 양 2");
        replyRepository.saveAndFlush(Reply.text(worry, senior, "첫 답장"));

        assertThatThrownBy(() -> replyRepository.saveAndFlush(Reply.text(worry, senior, "두 번째 답장")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 유효_답장_수는_삭제와_숨김을_제외하고_센다() {
        replyRepository.save(Reply.text(worry, persistUser(Role.SENIOR, "다정한 양 2"), "보이는 답장"));
        Reply hidden = Reply.text(worry, persistUser(Role.SENIOR, "다정한 양 3"), "숨김");
        hidden.hide();
        replyRepository.save(hidden);
        Reply deleted = Reply.text(worry, persistUser(Role.SENIOR, "다정한 양 4"), "삭제");
        deleted.delete();
        replyRepository.save(deleted);
        Reply both = Reply.text(worry, persistUser(Role.SENIOR, "다정한 양 5"), "삭제 후 숨김");
        both.hide();
        both.delete();
        replyRepository.saveAndFlush(both);

        assertThat(replyRepository.countVisibleByWorryId(worry.getId())).isEqualTo(1);
        assertThat(replyRepository.existsVisibleByWorryId(worry.getId())).isTrue();
    }

    @Test
    void 삭제나_숨김_답장만_있으면_유효_답장은_없다() {
        Reply hidden = Reply.text(worry, persistUser(Role.SENIOR, "다정한 양 2"), "숨김");
        hidden.hide();
        replyRepository.save(hidden);
        Reply deleted = Reply.text(worry, persistUser(Role.SENIOR, "다정한 양 3"), "삭제");
        deleted.delete();
        replyRepository.saveAndFlush(deleted);

        assertThat(replyRepository.countVisibleByWorryId(worry.getId())).isZero();
        assertThat(replyRepository.existsVisibleByWorryId(worry.getId())).isFalse();
    }

    @Test
    void 답장이_없는_고민과_다른_고민의_답장은_세지_않는다() {
        Worry other = Worry.of(youth, "다른 제목", "다른 본문", WorryTag.LOVE, WorryVisibility.PUBLIC);
        entityManager.persist(other);
        replyRepository.saveAndFlush(Reply.text(other, persistUser(Role.SENIOR, "다정한 양 2"), "다른 고민의 답장"));

        assertThat(replyRepository.countVisibleByWorryId(worry.getId())).isZero();
        assertThat(replyRepository.existsVisibleByWorryId(worry.getId())).isFalse();
    }

    @Test
    void 재답장_방지는_삭제와_숨김_답장도_포함한다() {
        User deletedSenior = persistUser(Role.SENIOR, "다정한 양 2");
        Reply deleted = Reply.text(worry, deletedSenior, "삭제");
        deleted.delete();
        replyRepository.save(deleted);
        User hiddenSenior = persistUser(Role.SENIOR, "다정한 양 3");
        Reply hidden = Reply.text(worry, hiddenSenior, "숨김");
        hidden.hide();
        replyRepository.saveAndFlush(hidden);
        User newcomer = persistUser(Role.SENIOR, "다정한 양 4");

        assertThat(replyRepository.existsByWorryIdAndSeniorId(worry.getId(), deletedSenior.getId())).isTrue();
        assertThat(replyRepository.existsByWorryIdAndSeniorId(worry.getId(), hiddenSenior.getId())).isTrue();
        assertThat(replyRepository.existsByWorryIdAndSeniorId(worry.getId(), newcomer.getId())).isFalse();
    }

    private User persistUser(Role role, String nickname) {
        User user = User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, nickname);
        entityManager.persist(user);
        return user;
    }
}
