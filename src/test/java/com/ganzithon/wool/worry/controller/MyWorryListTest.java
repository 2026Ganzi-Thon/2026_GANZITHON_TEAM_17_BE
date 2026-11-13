package com.ganzithon.wool.worry.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
class MyWorryListTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private JdbcTemplate jdbc;

    @Autowired private ReplyRepository replies;

    // 어르신 한 명은 같은 고민에 한 번만 답장할 수 있어서 답장마다 다른 어르신을 만든다.
    private void saveReply(Worry worry, String seniorNickname, boolean hidden, boolean deleted, boolean viewed) {
        User replier = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, seniorNickname));
        Reply reply = Reply.text(worry, replier, "답장");
        if (hidden) reply.hide();
        if (deleted) reply.delete();
        if (viewed) reply.markFirstViewed(LocalDateTime.now());
        replies.saveAndFlush(reply);
    }

    @Test
    void 삭제한_고민은_내_고민_목록에서_제외된다() throws Exception {
        User me = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "삭제목록청년"));
        Worry deleted = worries.saveAndFlush(Worry.of(me, "삭제할 고민", "내용", WorryTag.MIND, WorryVisibility.PUBLIC));
        Worry remaining = worries.saveAndFlush(Worry.of(me, "남길 고민", "내용", WorryTag.CAREER, WorryVisibility.PUBLIC));
        saveReply(deleted, "목록선배1", false, false, false);
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
        saveReply(first, "목록선배2", false, false, true);
        saveReply(first, "목록선배3", false, false, false);
        saveReply(first, "목록선배4", true, false, false);
        saveReply(first, "목록선배5", false, true, false);
        saveReply(hidden, "목록선배6", true, false, false);

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
