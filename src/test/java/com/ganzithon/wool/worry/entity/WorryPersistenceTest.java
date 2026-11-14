package com.ganzithon.wool.worry.entity;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class WorryPersistenceTest extends IntegrationTestSupport {
    @Autowired
    private EntityManager entityManager;

    @Test
    void 고민을_저장하고_다시_조회하면_작성자와_원문_및_상태가_유지된다() {
        User author = User.join(Role.YOUTH, AgeGroup.AGE_20S, "포근한 양 1");
        entityManager.persist(author);
        Worry worry = Worry.of(author, "고민 제목", "원문 그대로", WorryTag.CAREER, WorryVisibility.PUBLIC);
        entityManager.persist(worry);
        entityManager.flush();
        Long id = worry.getId();
        entityManager.clear();

        Worry saved = entityManager.find(Worry.class, id);
        assertThat(saved.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(saved.getTitle()).isEqualTo("고민 제목");
        assertThat(saved.getContent()).isEqualTo("원문 그대로");
        assertThat(saved.getTag()).isEqualTo(WorryTag.CAREER);
        assertThat(saved.getVisibility()).isEqualTo(WorryVisibility.PUBLIC);
        assertThat(saved.isHidden()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.isDeleted()).isFalse();

        saved.delete();
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(Worry.class, id).getDeletedAt()).isNotNull();
    }
}
