package com.ganzithon.wool.feedback;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.feedback.repository.ThanksRepository;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.repository.NotificationRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class ThanksControllerTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorryRepository worries;
    @Autowired ReplyRepository replies;
    @Autowired ThanksRepository thanks;
    @Autowired NotificationRepository notifications;
    User youth;
    User senior;
    Reply reply;

    @BeforeEach
    void setup() {
        youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "고마움청년"));
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "고마움선배"));
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.SENIORS_ONLY));
        reply = replies.saveAndFlush(Reply.text(worry, senior, "답장"));
    }

    ResultActions send(User sender, Long id, String body) throws Exception {
        var request = post("/api/replies/{replyId}/thanks", id).contentType(MediaType.APPLICATION_JSON).content(body);
        if (sender != null) request.header("X-User-Id", sender.getId());
        return mvc.perform(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GRATEFUL", "HELPED", "CHEERED"})
    void 도장과_한마디를_저장하고_어르신에게_알린다(String stamp) throws Exception {
        send(youth, reply.getId(), "{\"stamp\":\"" + stamp + "\",\"message\":\" 고마워요 \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("THANKS_201_1"))
                .andExpect(jsonPath("$.data.replyId").value(reply.getId()))
                .andExpect(jsonPath("$.data.stamp").value(stamp))
                .andExpect(jsonPath("$.data.message").value(" 고마워요 "))
                .andExpect(jsonPath("$.data.createdAt").exists());
        assertThat(thanks.existsByReplyId(reply.getId())).isTrue();
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId()))
                .anyMatch(n -> n.getType() == NotificationType.THANKS_ARRIVED && n.getTargetId() == null);
    }

    @Test void 한마디_생략과_중복_요청() throws Exception {
        send(youth, reply.getId(), "{\"stamp\":\"HELPED\"}").andExpect(status().isCreated());
        send(youth, reply.getId(), "{\"stamp\":\"CHEERED\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THANKS_409_001"));
        assertThat(notifications.findByUserIdOrderByCreatedAtDescIdDesc(senior.getId())).hasSize(1);
    }

    @Test void 권한과_답장_상태를_확인한다() throws Exception {
        User other = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "다른청년"));
        String body = "{\"stamp\":\"GRATEFUL\"}";
        send(null, reply.getId(), body).andExpect(status().isUnauthorized());
        send(senior, reply.getId(), body).andExpect(status().isForbidden());
        send(other, reply.getId(), body).andExpect(status().isForbidden());
        send(youth, 99999999L, body).andExpect(status().isNotFound());
        reply.hide(); replies.saveAndFlush(reply);
        send(youth, reply.getId(), body).andExpect(status().isForbidden());
        reply.delete(); replies.saveAndFlush(reply);
        send(youth, reply.getId(), body).andExpect(status().isNotFound());
        assertThat(thanks.existsByReplyId(reply.getId())).isFalse();
    }

    @Test void 이모지는_한_글자로_검사한다() {
        assertThat(new com.ganzithon.wool.feedback.request.CreateThanksRequest(
                com.ganzithon.wool.feedback.entity.ThanksStamp.GRATEFUL, "😀".repeat(255)).isMessageValid()).isTrue();
    }

    @Test void 입력과_내용을_검사한다() throws Exception {
        for (String body : new String[]{"{}", "{\"stamp\":0}", "{\"stamp\":\"UNKNOWN\"}",
                "{\"stamp\":\"GRATEFUL\",\"message\":123}",
                "{\"stamp\":\"GRATEFUL\",\"message\":\"" + "😀".repeat(256) + "\"}"}) {
            send(youth, reply.getId(), body).andExpect(status().isBadRequest());
        }
        send(youth, reply.getId(), "{\"stamp\":\"GRATEFUL\",\"message\":\"010-1234-5678\"}")
                .andExpect(status().isUnprocessableEntity());
        send(youth, reply.getId(), "{\"stamp\":\"GRATEFUL\",\"message\":\"" + "가".repeat(255) + "\"}")
                .andExpect(status().isCreated());
    }
}
