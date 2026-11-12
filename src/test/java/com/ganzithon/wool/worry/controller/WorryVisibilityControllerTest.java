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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class WorryVisibilityControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorryRepository worryRepository;
    @Autowired private EntityManager entityManager;

    private User owner;
    private User other;
    private User senior;
    private Worry worry;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "범위작성자"));
        other = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_30S, "범위타인"));
        senior = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "범위선배"));
        worry = worryRepository.saveAndFlush(Worry.of(owner, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String userId, Long id, String body) {
        var request = patch("/api/worries/{worryId}/visibility", id)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return userId == null ? request : request.header("X-User-Id", userId);
    }

    @Test
    void 작성자가_전체공개를_비공개로_변경한다() throws Exception {
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"visibility\":\"SENIORS_ONLY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WORRY_200_3"))
                .andExpect(jsonPath("$.message").value("공개 범위를 변경했습니다."))
                .andExpect(jsonPath("$.data.worryId").value(worry.getId()))
                .andExpect(jsonPath("$.data.visibility").value("SENIORS_ONLY"));
        worryRepository.flush();
        entityManager.clear();
        assertThat(worryRepository.findById(worry.getId()).orElseThrow().getVisibility()).isEqualTo(WorryVisibility.SENIORS_ONLY);
    }

    @Test
    void 신고로_숨겨진_고민은_공개_범위를_변경할_수_없다() throws Exception {
        entityManager.createNativeQuery("UPDATE worries SET hidden = true WHERE id = :id")
                .setParameter("id", worry.getId()).executeUpdate();
        entityManager.clear();
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"visibility\":\"SENIORS_ONLY\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
        assertThat(worryRepository.findById(worry.getId()).orElseThrow().getVisibility()).isEqualTo(WorryVisibility.PUBLIC);
    }

    @Test
    void 이미_비공개이면_다시_변경하거나_공개로_바꿀_수_없다() throws Exception {
        worry.makeSeniorsOnly();
        worryRepository.flush();
        for (String visibility : new String[]{"SENIORS_ONLY", "PUBLIC"}) {
            mockMvc.perform(request(owner.getId(), worry.getId(), "{\"visibility\":\"" + visibility + "\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORRY_409_001"));
        }
        assertThat(worry.getVisibility()).isEqualTo(WorryVisibility.SENIORS_ONLY);
    }

    @Test
    void 타인과_어르신은_변경할_수_없고_없는_고민은_404다() throws Exception {
        String body = "{\"visibility\":\"SENIORS_ONLY\"}";
        mockMvc.perform(request(other.getId(), worry.getId(), body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORRY_403_002"));
        mockMvc.perform(request(senior.getId(), worry.getId(), body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
        mockMvc.perform(request(owner.getId(), Long.MAX_VALUE, body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));
        assertThat(worry.getVisibility()).isEqualTo(WorryVisibility.PUBLIC);
    }

    @Test
    void 삭제된_고민과_인증되지_않은_요청을_거부한다() throws Exception {
        worry.delete();
        worryRepository.flush();
        String body = "{\"visibility\":\"SENIORS_ONLY\"}";
        mockMvc.perform(request(owner.getId(), worry.getId(), body)).andExpect(status().isNotFound());
        mockMvc.perform(request(null, worry.getId(), body)).andExpect(status().isUnauthorized());
        mockMvc.perform(request(UUID.randomUUID().toString(), worry.getId(), body)).andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_요청값은_변경하지_않는다() throws Exception {
        for (String body : new String[]{"{}", "{\"visibility\":null}", "{\"visibility\":0}",
                "{\"visibility\":\"INVALID\"}", "{\"visibility\":\"PUBLIC\"}"}) {
            mockMvc.perform(request(owner.getId(), worry.getId(), body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        }
        assertThat(worry.getVisibility()).isEqualTo(WorryVisibility.PUBLIC);
    }
}
