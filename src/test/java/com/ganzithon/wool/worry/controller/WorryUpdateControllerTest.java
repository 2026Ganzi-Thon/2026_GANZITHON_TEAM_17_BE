package com.ganzithon.wool.worry.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.filter.client.SolarClient;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class WorryUpdateControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoBean private SolarClient solarClient;
    private User owner;
    private User other;
    private User senior;
    private Worry worry;

    @BeforeAll
    void createRepliesTable() {
        jdbc.execute("CREATE TABLE replies (id BIGINT PRIMARY KEY, worry_id BIGINT NOT NULL, hidden BOOLEAN NOT NULL, deleted_at TIMESTAMP NULL)");
    }

    @AfterAll
    void dropRepliesTable() {
        jdbc.execute("DROP TABLE replies");
    }

    @BeforeEach
    void setUp() {
        owner = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "수정작성자"));
        other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_30S, "수정타인"));
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "수정선배"));
        worry = worries.saveAndFlush(Worry.of(owner, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }

    private MockHttpServletRequestBuilder request(String userId, long id, String body) {
        var request = patch("/api/worries/{worryId}", id).contentType(MediaType.APPLICATION_JSON).content(body);
        return userId == null ? request : request.header("X-User-Id", userId);
    }

    @Test
    void 전달한_내용만_수정하고_위기_감지_결과를_반환한다() throws Exception {
        when(solarClient.isConfigured()).thenReturn(true);
        when(solarClient.isCrisis("힘든 본문")).thenReturn(true);
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"content\":\"힘든 본문\",\"tag\":\"HEALTH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WORRY_200_6"))
                .andExpect(jsonPath("$.data.worryId").value(worry.getId()))
                .andExpect(jsonPath("$.data.crisisDetected").value(true))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        entityManager.clear();
        Worry saved = worries.findById(worry.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("힘든 본문");
        assertThat(saved.getTag()).isEqualTo(WorryTag.HEALTH);
        assertThat(saved.getVisibility()).isEqualTo(WorryVisibility.PUBLIC);
        verify(solarClient).isCrisis("힘든 본문");
    }

    @Test
    void 본문_없이_제목만_수정하면_위기_감지를_호출하지_않는다() throws Exception {
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"title\":\"새 제목\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.crisisDetected").value(false));
        assertThat(worry.getContent()).isEqualTo("본문");
        verifyNoInteractions(solarClient);
    }

    @Test
    void 삭제되거나_숨겨진_답장만_있으면_수정할_수_있다() throws Exception {
        jdbc.update("INSERT INTO replies VALUES (1, ?, TRUE, NULL)", worry.getId());
        jdbc.update("INSERT INTO replies VALUES (2, ?, FALSE, CURRENT_TIMESTAMP)", worry.getId());
        jdbc.update("INSERT INTO replies VALUES (3, ?, TRUE, CURRENT_TIMESTAMP)", worry.getId());
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"title\":\"새 제목\"}"))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(worries.findById(worry.getId()).orElseThrow().getTitle()).isEqualTo("새 제목");
    }

    @Test
    void 삭제되거나_숨겨지지_않은_답장이_하나라도_있으면_수정할_수_없다() throws Exception {
        jdbc.update("INSERT INTO replies VALUES (1, ?, TRUE, NULL)", worry.getId());
        jdbc.update("INSERT INTO replies VALUES (2, ?, FALSE, CURRENT_TIMESTAMP)", worry.getId());
        jdbc.update("INSERT INTO replies VALUES (3, ?, FALSE, NULL)", worry.getId());
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"title\":\"새 제목\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORRY_409_002"));
        assertThat(worry.getTitle()).isEqualTo("제목");
    }

    @Test
    void 인증_권한과_대상_존재를_검사한다() throws Exception {
        String body = "{\"title\":\"새 제목\"}";
        mockMvc.perform(request(null, worry.getId(), body)).andExpect(status().isUnauthorized());
        mockMvc.perform(request(senior.getId(), worry.getId(), body)).andExpect(status().isForbidden());
        mockMvc.perform(request(other.getId(), worry.getId(), body)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORRY_403_004"));
        mockMvc.perform(request(owner.getId(), Long.MAX_VALUE, body)).andExpect(status().isNotFound());
        worry.delete();
        worries.flush();
        mockMvc.perform(request(owner.getId(), worry.getId(), body)).andExpect(status().isNotFound());
        verifyNoInteractions(solarClient);
    }

    @Test
    void 잘못된_입력과_차단된_내용은_반영하지_않는다() throws Exception {
        for (String body : new String[]{"{}", "{\"title\":\" \"}", "{\"title\":\"" + "가".repeat(41) + "\"}",
                "{\"tag\":0}", "{\"tag\":\"PARENTING\"}", "{\"title\":null,\"tag\":\"LOVE\"}"}) {
            mockMvc.perform(request(owner.getId(), worry.getId(), body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(request(owner.getId(), worry.getId(), "{\"content\":\"010-1234-5678\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FILTER_422_002"));
        assertThat(worry.getContent()).isEqualTo("본문");
        verifyNoInteractions(solarClient);
    }
}
