package com.ganzithon.wool.worry.controller;

import com.ganzithon.wool.IntegrationTestSupport;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class WorryDeleteControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private EntityManager entityManager;
    private User owner;
    private User other;
    private User senior;
    private Worry worry;

    @BeforeEach
    void setUp() {
        owner = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "삭제작성자"));
        other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_30S, "삭제타인"));
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "삭제선배"));
        worry = worries.saveAndFlush(Worry.of(owner, "고민 제목", "고민 본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private MockHttpServletRequestBuilder request(String userId, Long id) {
        var request = delete("/api/worries/{worryId}", id);
        return userId == null ? request : request.header("X-User-Id", userId);
    }

    @Test
    void 작성자가_고민을_소프트_삭제한다() throws Exception {
        Long worryId = worry.getId();
        mockMvc.perform(request(owner.getId(), worryId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WORRY_200_5"))
                .andExpect(jsonPath("$.message").value("고민을 삭제했습니다."))
                .andExpect(jsonPath("$.data.worryId").value(worryId));
        worries.flush();
        entityManager.clear();
        Worry saved = worries.findById(worryId).orElseThrow();
        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("고민 제목");
        mockMvc.perform(request(owner.getId(), worryId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
    }

    @Test
    void 신고로_숨겨진_내_고민도_삭제할_수_있다() throws Exception {
        Long worryId = worry.getId();
        entityManager.createNativeQuery("UPDATE worries SET hidden = true WHERE id = :id")
                .setParameter("id", worryId).executeUpdate();
        entityManager.clear();
        mockMvc.perform(request(owner.getId(), worryId)).andExpect(status().isOk());
        worries.flush();
        entityManager.clear();
        assertThat(worries.findById(worryId).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void 인증과_권한이_없거나_대상이_없으면_삭제할_수_없다() throws Exception {
        mockMvc.perform(request(null, worry.getId())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
        mockMvc.perform(request(UUID.randomUUID().toString(), worry.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(request(senior.getId(), worry.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_403_001"));
        mockMvc.perform(request(other.getId(), worry.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORRY_403_003"))
                .andExpect(jsonPath("$.message").value("삭제할 권한이 없습니다."));
        mockMvc.perform(request(owner.getId(), Long.MAX_VALUE)).andExpect(status().isNotFound());
        worries.flush();
        entityManager.clear();
        assertThat(worries.findById(worry.getId()).orElseThrow().getDeletedAt()).isNull();
    }
}
