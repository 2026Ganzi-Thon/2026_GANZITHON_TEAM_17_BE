package com.ganzithon.wool.certificate.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import com.ganzithon.wool.certificate.entity.Certificate;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// WOOL-003 감사장 목록 조회, WOOL-004 감사장 상세 조회
@AutoConfigureMockMvc
@Transactional
class CertificateControllerTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private CertificateRepository certificates;

    private User youth;
    private User senior;
    private User other;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private Certificate issue(User owner, int replyCount, LocalDateTime issuedAt) {
        return certificates.saveAndFlush(Certificate.issue(owner, replyCount, issuedAt));
    }

    private Reply seedReply(User by) {
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        return replies.saveAndFlush(Reply.text(worry, by, "답장"));
    }

    private ResultActions list(User who) throws Exception {
        var request = get("/api/senior/certificates");
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    private ResultActions detail(User who, Long certificateId) throws Exception {
        var request = get("/api/senior/certificates/{certificateId}", certificateId);
        return mockMvc.perform(who == null ? request : request.header("X-User-Id", who.getId()));
    }

    @BeforeEach
    void setUp() {
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "선배");
        other = newUser(Role.SENIOR, "다른선배");
    }

    // ================= WOOL-003 목록 =================

    @Test
    void 목록은_발급일_최신순으로_내려주고_이미지_주소는_주지_않는다() throws Exception {
        Certificate first = issue(senior, 20, LocalDateTime.of(2026, 11, 20, 10, 0));
        Certificate third = issue(senior, 60, LocalDateTime.of(2027, 1, 5, 9, 30));
        Certificate second = issue(senior, 40, LocalDateTime.of(2026, 12, 15, 10, 0));

        list(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("CERT_200_1"))
                .andExpect(jsonPath("$.message").value("감사장 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].certificateId").value(third.getId()))
                .andExpect(jsonPath("$.data.items[0].replyCount").value(60))
                .andExpect(jsonPath("$.data.items[0].issuedAt").value("2027-01-05T09:30:00+09:00"))
                .andExpect(jsonPath("$.data.items[1].certificateId").value(second.getId()))
                .andExpect(jsonPath("$.data.items[1].replyCount").value(40))
                .andExpect(jsonPath("$.data.items[2].certificateId").value(first.getId()))
                .andExpect(jsonPath("$.data.items[2].replyCount").value(20))
                .andExpect(jsonPath("$.data.items[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].pdfUrl").doesNotExist());
    }

    @Test
    void 발급일이_같으면_나중에_만든_감사장이_먼저다() throws Exception {
        LocalDateTime same = LocalDateTime.of(2026, 11, 20, 10, 0);
        Certificate older = issue(senior, 20, same);
        Certificate newer = issue(senior, 40, same);

        list(senior)
                .andExpect(jsonPath("$.data.items[0].certificateId").value(newer.getId()))
                .andExpect(jsonPath("$.data.items[1].certificateId").value(older.getId()));
    }

    @Test
    void 감사장이_없으면_빈_배열이다() throws Exception {
        list(senior)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 다른_어르신의_감사장은_목록에_나오지_않는다() throws Exception {
        Certificate mine = issue(senior, 20, LocalDateTime.of(2026, 11, 20, 10, 0));
        issue(other, 20, LocalDateTime.of(2026, 11, 21, 10, 0));
        issue(other, 40, LocalDateTime.of(2026, 11, 22, 10, 0));

        list(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].certificateId").value(mine.getId()));
    }

    @Test
    void 스무_번째_답장을_보내면_그_감사장이_목록에_나타난다() throws Exception {
        for (int i = 0; i < 19; i++) {
            seedReply(senior);
        }
        Worry twentieth = worries.saveAndFlush(Worry.of(youth, "스무 번째", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));

        mockMvc.perform(post("/api/worries/{worryId}/replies", twentieth.getId())
                        .header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"스무 번째 답장\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.certificateIssued").value(true));

        list(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].replyCount").value(20));
    }

    @Test
    void 답장을_삭제해도_이미_받은_감사장은_목록에_그대로_있다() throws Exception {
        Reply reply = seedReply(senior);
        Certificate certificate = issue(senior, 20, LocalDateTime.of(2026, 11, 20, 10, 0));

        mockMvc.perform(delete("/api/replies/{replyId}", reply.getId()).header("X-User-Id", senior.getId()))
                .andExpect(status().isOk());

        list(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].certificateId").value(certificate.getId()));
    }

    // 발급부터 삭제까지 실제 흐름을 잇는다. 감사장을 만든 바로 그 20번째 답장을 지워도 감사장은 남고, 누적 수만 줄어든다.
    @Test
    void 스무_번째_답장으로_발급된_감사장은_그_답장을_삭제해도_남는다() throws Exception {
        for (int i = 0; i < 19; i++) {
            seedReply(senior);
        }
        Worry twentieth = worries.saveAndFlush(Worry.of(youth, "스무 번째", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));

        String body = mockMvc.perform(post("/api/worries/{worryId}/replies", twentieth.getId())
                        .header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"스무 번째 답장\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.certificateIssued").value(true))
                .andReturn().getResponse().getContentAsString();
        Number replyId = JsonPath.read(body, "$.data.replyId");

        mockMvc.perform(delete("/api/replies/{replyId}", replyId.longValue()).header("X-User-Id", senior.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalReplies").value(19));

        list(senior)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].replyCount").value(20));
    }

    @Test
    void 목록은_헤더가_없으면_401_청년이면_403이다() throws Exception {
        list(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        list(youth).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }

    // ================= WOOL-004 상세 =================

    @Test
    void 상세는_닉네임과_답장_수와_발급일을_주고_이미지_주소는_주지_않는다() throws Exception {
        Certificate certificate = issue(senior, 40, LocalDateTime.of(2026, 12, 15, 10, 0));

        detail(senior, certificate.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("CERT_200_2"))
                .andExpect(jsonPath("$.message").value("감사장 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.certificateId").value(certificate.getId()))
                .andExpect(jsonPath("$.data.nickname").value(senior.getNickname()))
                .andExpect(jsonPath("$.data.replyCount").value(40))
                .andExpect(jsonPath("$.data.issuedAt").value("2026-12-15T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.pdfUrl").doesNotExist());
    }

    @Test
    void 닉네임을_바꾸면_이미_받은_감사장에도_바뀐_닉네임이_나온다() throws Exception {
        Certificate certificate = issue(senior, 20, LocalDateTime.of(2026, 11, 20, 10, 0));
        senior.changeNickname("새 이름 7");
        users.saveAndFlush(senior);

        detail(senior, certificate.getId()).andExpect(jsonPath("$.data.nickname").value("새 이름 7"));
    }

    @Test
    void 남의_감사장은_403이다() throws Exception {
        Certificate others = issue(other, 20, LocalDateTime.of(2026, 11, 20, 10, 0));

        detail(senior, others.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CERT_403_001"))
                .andExpect(jsonPath("$.message").value("볼 수 없는 감사장입니다."));
    }

    @Test
    void 없는_감사장은_404다() throws Exception {
        detail(senior, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERT_404_001"))
                .andExpect(jsonPath("$.message").value("감사장을 찾을 수 없습니다."));
    }

    @Test
    void 상세는_헤더가_없으면_401_청년이면_403이다() throws Exception {
        Certificate certificate = issue(senior, 20, LocalDateTime.of(2026, 11, 20, 10, 0));

        detail(null, certificate.getId()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER_401_001"));
        detail(youth, certificate.getId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_403_001"));
    }
}
