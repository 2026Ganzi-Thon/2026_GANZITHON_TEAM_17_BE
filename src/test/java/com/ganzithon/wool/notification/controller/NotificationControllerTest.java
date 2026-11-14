package com.ganzithon.wool.notification.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.notification.entity.Notification;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.notification.service.NotificationService;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;

    private User youth;
    private User senior;

    @BeforeEach
    void setUp() {
        youth = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "알림청년"));
        senior = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "알림선배"));
    }

    @Test
    void 내_알림만_최신순으로_명세의_형식에_맞게_조회한다() throws Exception {
        Notification first = notificationService.send(youth.getId(), NotificationType.REPLY_ARRIVED,
                "답장이 도착했어요", null, TargetKind.REPLY, 501L);
        notificationService.send(senior.getId(), NotificationType.THANKS_ARRIVED,
                "선배님의 알림", null, TargetKind.RECEIVED, null);
        Notification second = notificationService.send(youth.getId(), NotificationType.REPLY_ARRIVED,
                "새 답장이 도착했어요", null, TargetKind.REPLY, 502L);

        mockMvc.perform(get("/api/notifications").header("X-User-Id", youth.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTI_200_1"))
                .andExpect(jsonPath("$.message").value("알림 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].notificationId").value(second.getId()))
                .andExpect(jsonPath("$.data.items[0].type").value("REPLY_ARRIVED"))
                .andExpect(jsonPath("$.data.items[0].title").value("새 답장이 도착했어요"))
                .andExpect(jsonPath("$.data.items[0].target.kind").value("REPLY"))
                .andExpect(jsonPath("$.data.items[0].target.id").value(502))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].createdAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.items[1].notificationId").value(first.getId()));
        mockMvc.perform(get("/api/notifications").header("X-User-Id", senior.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].target.kind").value("RECEIVED"))
                .andExpect(jsonPath("$.data.items[0].target.id").value((Object) null));
    }

    @Test
    void 알림이_없으면_빈_목록을_반환한다() throws Exception {
        mockMvc.perform(get("/api/notifications").header("X-User-Id", youth.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 내_알림을_반복해서_읽음_처리해도_성공하고_목록에_반영된다() throws Exception {
        Notification notification = notificationService.send(senior.getId(), NotificationType.THANKS_ARRIVED,
                "고마움이 도착했어요", null, TargetKind.RECEIVED, null);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(patch("/api/notifications/{notificationId}/read", notification.getId())
                            .header("X-User-Id", senior.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("NOTI_200_2"))
                    .andExpect(jsonPath("$.message").value("알림을 읽음 처리했습니다."))
                    .andExpect(jsonPath("$.data.notificationId").value(notification.getId()))
                    .andExpect(jsonPath("$.data.read").value(true));
        }
        mockMvc.perform(get("/api/notifications").header("X-User-Id", senior.getId()))
                .andExpect(jsonPath("$.data.items[0].read").value(true));
    }

    @Test
    void 다른_사용자와_없는_알림에_대한_읽음_요청을_거부한다() throws Exception {
        Notification notification = notificationService.send(senior.getId(), NotificationType.THANKS_ARRIVED,
                "선배님의 알림", null, TargetKind.RECEIVED, null);
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notification.getId())
                        .header("X-User-Id", youth.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOTI_403_001"));
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().isRead()).isFalse();
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", Long.MAX_VALUE)
                        .header("X-User-Id", youth.getId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTI_404_001"));
    }

    @Test
    void 두_API는_헤더가_없거나_등록되지_않은_사용자를_거부한다() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        mockMvc.perform(get("/api/notifications").header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 1))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 1)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
    }
}
