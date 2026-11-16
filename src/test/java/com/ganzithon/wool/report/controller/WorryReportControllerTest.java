package com.ganzithon.wool.report.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.report.repository.WorryReportRepository;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class WorryReportControllerTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired WorryReportRepository reports;
    User youth; User other; User senior; Worry worry;
    private static final String PATH = "/api/worries/{id}/reports";
    @BeforeEach void setup() {
        youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "신고작성자"));
        other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "신고청년"));
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "신고선배"));
        worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
    }
    @Test void 청년이_신고하면_숨기고_중복은_409다() throws Exception {
        var request = post(PATH, worry.getId()).header("X-User-Id", other.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"AD\"}");
        mvc.perform(request).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("REPORT_201_2")).andExpect(jsonPath("$.data.hidden").value(true));
        assertThat(reports.existsByWorryIdAndReporterId(worry.getId(), other.getId())).isTrue();
        assertThat(worries.findById(worry.getId()).orElseThrow().isHidden()).isTrue();
        mvc.perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_409_002"));
        mvc.perform(post(PATH, worry.getId()).header("X-User-Id", senior.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ETC\"}")).andExpect(status().isNotFound());
    }
    @Test void 권한과_입력을_검증한다() throws Exception {
        String valid = "{\"reason\":\"ABUSE\"}";
        mvc.perform(post(PATH, worry.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH, worry.getId()).header("X-User-Id", youth.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REPORT_403_001"));
        for (String body : new String[]{"{}", "{\"reason\":0}", "{\"reason\":\"UNKNOWN\"}"}) {
            mvc.perform(post(PATH, worry.getId()).header("X-User-Id", other.getId()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        Worry privateWorry = worries.saveAndFlush(Worry.of(youth, "비공개", "본문", WorryTag.MIND, WorryVisibility.SENIORS_ONLY));
        mvc.perform(post(PATH, privateWorry.getId()).header("X-User-Id", other.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORRY_403_001"));
        mvc.perform(post(PATH, privateWorry.getId()).header("X-User-Id", senior.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isCreated());
        mvc.perform(post(PATH, 9999999).header("X-User-Id", senior.getId()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isNotFound());
    }
}
