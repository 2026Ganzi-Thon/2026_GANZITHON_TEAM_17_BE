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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class MyWorryListTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private JdbcTemplate jdbc;

    // 테스트 프로필은 Flyway가 꺼져 있고 Reply 엔티티가 아직 없어 조회에 필요한 컬럼만 준비한다.
    @BeforeAll
    void createRepliesTable() {
        jdbc.execute("CREATE TABLE replies (id BIGINT PRIMARY KEY, worry_id BIGINT NOT NULL, hidden BOOLEAN NOT NULL, deleted_at TIMESTAMP NULL, first_viewed_at TIMESTAMP NULL)");
    }

    @AfterAll
    void dropRepliesTable() {
        jdbc.execute("DROP TABLE replies");
    }

    @Test
    void 삭제한_고민은_내_고민_목록에서_제외된다() throws Exception {
        User me = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "삭제목록청년"));
        Worry deleted = worries.saveAndFlush(Worry.of(me, "삭제할 고민", "내용", WorryTag.MIND, WorryVisibility.PUBLIC));
        Worry remaining = worries.saveAndFlush(Worry.of(me, "남길 고민", "내용", WorryTag.CAREER, WorryVisibility.PUBLIC));
        jdbc.update("INSERT INTO replies VALUES (1, ?, FALSE, NULL, NULL)", deleted.getId());
        mockMvc.perform(delete("/api/worries/{worryId}", deleted.getId()).header("X-User-Id", me.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/worries/me").header("X-User-Id", me.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].worryId").value(remaining.getId()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM replies WHERE worry_id = ?", Integer.class, deleted.getId()))
                .isEqualTo(1);
    }

    @Test
    void 내_고민만_조회하고_숨김_답장과_삭제된_답장은_집계하지_않는다() throws Exception {
        User me = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "목록청년"));
        User other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_30S, "다른청년"));
        Worry first = worries.saveAndFlush(Worry.of(me, "첫 고민", "내용", WorryTag.MIND, WorryVisibility.PUBLIC));
        Worry hidden = worries.saveAndFlush(Worry.of(me, "숨긴 고민", "내용", WorryTag.LOVE, WorryVisibility.SENIORS_ONLY));
        Worry deleted = worries.saveAndFlush(Worry.of(me, "삭제 고민", "내용", WorryTag.LIVING, WorryVisibility.PUBLIC));
        worries.saveAndFlush(Worry.of(other, "남의 고민", "내용", WorryTag.CAREER, WorryVisibility.PUBLIC));
        deleted.delete();
        worries.flush();
        jdbc.update("UPDATE worries SET hidden = TRUE WHERE id = ?", hidden.getId());
        jdbc.update("INSERT INTO replies VALUES (1, ?, FALSE, NULL, CURRENT_TIMESTAMP)", first.getId());
        jdbc.update("INSERT INTO replies VALUES (2, ?, FALSE, NULL, NULL)", first.getId());
        jdbc.update("INSERT INTO replies VALUES (3, ?, TRUE, NULL, NULL)", first.getId());
        jdbc.update("INSERT INTO replies VALUES (4, ?, FALSE, CURRENT_TIMESTAMP, NULL)", first.getId());
        jdbc.update("INSERT INTO replies VALUES (5, ?, TRUE, NULL, NULL)", hidden.getId());

        mockMvc.perform(get("/api/worries/me").header("X-User-Id", me.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WORRY_200_1"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].worryId").value(hidden.getId()))
                .andExpect(jsonPath("$.data.items[0].hidden").value(true))
                .andExpect(jsonPath("$.data.items[0].replyCount").value(0))
                .andExpect(jsonPath("$.data.items[0].hasUnreadReply").value(false))
                .andExpect(jsonPath("$.data.items[1].worryId").value(first.getId()))
                .andExpect(jsonPath("$.data.items[1].replyCount").value(2))
                .andExpect(jsonPath("$.data.items[1].hasUnreadReply").value(true))
                .andExpect(jsonPath("$.data.items[1].createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void 빈_목록과_접근_권한을_확인한다() throws Exception {
        User me = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "빈목록청년"));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "목록선배"));
        mockMvc.perform(get("/api/worries/me").header("X-User-Id", me.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/worries/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/worries/me").header("X-User-Id", senior.getId())).andExpect(status().isForbidden());
    }
}
