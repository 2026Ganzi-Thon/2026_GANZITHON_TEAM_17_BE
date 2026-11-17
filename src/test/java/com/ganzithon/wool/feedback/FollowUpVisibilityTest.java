package com.ganzithon.wool.feedback;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.entity.FollowUp;
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class FollowUpVisibilityTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired ReplyRepository replies;
    @Autowired FollowUpRepository followUps;
    User youth; User senior; User other; Reply reply;
    private static final String PATH = "/api/replies/{id}/follow-ups/visibility";
    private static final String PRIVATE = "{\"visibility\":\"SENIORS_ONLY\"}";
    @BeforeEach void setup() {
        youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "공개청년"));
        other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "다른청년"));
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "공개선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
    }
    @Test void 공개에서_비공개로_변경하고_재요청은_충돌한다() throws Exception {
        followUps.saveAndFlush(FollowUp.of(reply, "후일담", WorryVisibility.PUBLIC));
        var request = patch(PATH, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("FOLLOWUP_200_1")).andExpect(jsonPath("$.data.visibility").value("SENIORS_ONLY"));
        mvc.perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FOLLOWUP_409_002"));
    }
    @Test void 권한과_입력_없는_대상을_검증한다() throws Exception {
        mvc.perform(patch(PATH, reply.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE)).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", senior.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE)).andExpect(status().isForbidden());
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", other.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE)).andExpect(status().isForbidden());
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content("{} ")).andExpect(status().isBadRequest());
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FOLLOWUP_404_001"));
        followUps.saveAndFlush(FollowUp.of(reply, "후일담", WorryVisibility.SENIORS_ONLY));
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PUBLIC\"}")).andExpect(status().isConflict());
        reply.hide(); replies.flush();
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE)).andExpect(status().isForbidden());
        reply.delete(); replies.flush();
        mvc.perform(patch(PATH, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(PRIVATE)).andExpect(status().isNotFound());
    }
}
