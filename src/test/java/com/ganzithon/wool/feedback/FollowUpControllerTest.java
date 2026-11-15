package com.ganzithon.wool.feedback;

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
import com.ganzithon.wool.feedback.repository.FollowUpRepository;
import com.ganzithon.wool.worry.service.WorryVisibilityService;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc @Transactional
class FollowUpControllerTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired ReplyRepository replies;
    @Autowired FollowUpRepository followUps;
    @Autowired WorryVisibilityService visibilityService;
    @Test void 후일담_작성과_중복을_확인한다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "후일담청년"));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "후일담선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
        var request = post("/api/replies/{id}/follow-ups", reply.getId()).header("X-User-Id", youth.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"고마워요\"}");
        mvc.perform(request).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("FOLLOWUP_201_1"))
                .andExpect(jsonPath("$.data.visibility").value("SENIORS_ONLY"));
        mvc.perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FOLLOWUP_409_001"));
    }
    @Test void 공개_후일담은_고민이_비공개로_변경되면_함께_비공개가_된다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "공개청년"));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "공개선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
        mvc.perform(post("/api/replies/{id}/follow-ups", reply.getId()).header("X-User-Id", youth.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"고마워요\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated());
        visibilityService.change(youth.getId(), worry.getId(), WorryVisibility.SENIORS_ONLY);
        followUps.flush();
        entityManager.clear();
        assertThat(followUps.findAll().get(0).getVisibility()).isEqualTo(WorryVisibility.SENIORS_ONLY);
    }

    @Test void 입력과_권한_오류를_확인한다() throws Exception {
        User youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "검증청년"));
        User other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "다른청년"));
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "검증선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.SENIORS_ONLY));
        Reply reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
        String path = "/api/replies/{id}/follow-ups";
        String valid = "{\"content\":\"감사합니다\"}";
        mvc.perform(post(path, reply.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized());
        mvc.perform(post(path, reply.getId()).header("X-User-Id", senior.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
        mvc.perform(post(path, reply.getId()).header("X-User-Id", other.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
        mvc.perform(post(path, 99999999).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isNotFound());
        for (String body : new String[]{"{}", "{\"content\":\"   \"}", "{\"content\":123}", "{\"content\":\"a\",\"visibility\":0}", "{\"content\":\"" + "가".repeat(1001) + "\"}"}) {
            mvc.perform(post(path, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"안녕\",\"visibility\":\"PUBLIC\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FOLLOWUP_400_001"));
        for (String text : new String[]{"씨발", "010-1234-5678", "https://example.com"}) {
            mvc.perform(post(path, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + text + "\"}")).andExpect(status().isUnprocessableEntity());
        }
        reply.hide(); replies.flush();
        mvc.perform(post(path, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
        reply.delete(); replies.flush();
        mvc.perform(post(path, reply.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isNotFound());
    }

}
