package com.ganzithon.wool.user.repository;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 저장하고_다시_읽으면_나이대와_기본_제재단계가_유지된다() {
        User saved = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS, "포근한 양 312"));

        User found = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getAgeGroup()).isEqualTo(AgeGroup.AGE_70S_PLUS);
        assertThat(found.getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(userRepository.existsByNickname("포근한 양 312")).isTrue();
    }
}
