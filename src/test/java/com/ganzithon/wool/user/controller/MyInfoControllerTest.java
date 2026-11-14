package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class MyInfoControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @Test
    void 청년의_정보와_제재_없음을_반환한다() throws Exception {
        User user = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "조회청년"));
        mockMvc.perform(get("/api/users/me").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_200_2"))
                .andExpect(jsonPath("$.message").value("내 정보 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.role").value("YOUTH"))
                .andExpect(jsonPath("$.data.nickname").value("조회청년"))
                .andExpect(jsonPath("$.data.ageGroup").value("20S"))
                .andExpect(jsonPath("$.data.sanction.level").value("NONE"))
                .andExpect(jsonPath("$.data.sanction.restrictedUntil").value((Object) null));
    }

    @Test
    void 어르신의_제한_상태와_해제_시각을_반환한다() throws Exception {
        User user = User.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS, "조회선배");
        user.applySanction(SanctionLevel.RESTRICTED, LocalDateTime.of(2026, 11, 20, 12, 30));
        userRepository.saveAndFlush(user);
        mockMvc.perform(get("/api/users/me").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SENIOR"))
                .andExpect(jsonPath("$.data.ageGroup").value("70S_PLUS"))
                .andExpect(jsonPath("$.data.sanction.level").value("RESTRICTED"))
                .andExpect(jsonPath("$.data.sanction.restrictedUntil").value(endsWith("+09:00")));
    }

    @Test
    void 헤더가_없거나_등록되지_않은_사용자는_거부한다() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        mockMvc.perform(get("/api/users/me").header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
    }
}
