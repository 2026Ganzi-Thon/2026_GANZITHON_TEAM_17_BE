package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.filter.client.OcrClient;
import com.ganzithon.wool.global.storage.FileStorage;
import com.ganzithon.wool.notification.repository.NotificationRepository;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 손편지 답장은 사진 속 글자를 OCR로 읽어 글 답장과 같은 검사를 한다.
// 검사 순서(400 → 404 → 403 → 409 → 422)와, OCR을 부를 필요가 없는 요청에서는 부르지 않는 것(무료 한도 보호)을 확인한다.
@AutoConfigureMockMvc
@Transactional
class ReplyCreateLetterOcrTest extends IntegrationTestSupport {

    @MockitoBean private OcrClient ocrClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Autowired private ReplyRepository replies;
    @Autowired private NotificationRepository notifications;
    @Autowired private FileStorage fileStorage;
    @Value("${file.storage.dir}") private String storageDir;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private String letterUrl;
    private User youth;
    private User senior;
    private Worry worry;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    private User newUser(Role role, String prefix) {
        return users.saveAndFlush(User.join(role, role == Role.YOUTH ? AgeGroup.AGE_20S : AgeGroup.AGE_60S, name(prefix)));
    }

    private ResultActions send(User sender, Long worryId, String body) throws Exception {
        return mockMvc.perform(post("/api/worries/{worryId}/replies", worryId)
                .contentType(MediaType.APPLICATION_JSON).content(body).header("X-User-Id", sender.getId()));
    }

    private ResultActions sendLetter(Long worryId) throws Exception {
        return send(senior, worryId, "{\"type\":\"LETTER\",\"imageUrl\":\"" + letterUrl + "\"}");
    }

    @BeforeEach
    void setUp() {
        letterUrl = fileStorage.save("letters", UUID.randomUUID() + ".png", PNG);
        youth = newUser(Role.YOUTH, "청년");
        senior = newUser(Role.SENIOR, "선배");
        worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        when(ocrClient.isConfigured()).thenReturn(true);
        when(ocrClient.extractText(any(), any())).thenReturn("");
    }

    @AfterEach
    void removeLetterFiles() throws Exception {
        FileSystemUtils.deleteRecursively(Path.of(storageDir).toAbsolutePath().resolve("letters"));
    }

    // ---------- 검사 결과 ----------

    @Test
    void 문제가_없는_손편지는_저장된_사진을_OCR에_넘기고_201이다() throws Exception {
        when(ocrClient.extractText(any(), eq("png"))).thenReturn("힘든 일이 많았지요. 고생했어요.");

        sendLetter(worry.getId()).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("REPLY_201_1"));

        verify(ocrClient).extractText(eq(PNG), eq("png"));
        assertThat(replies.existsByWorryIdAndSeniorId(worry.getId(), senior.getId())).isTrue();
    }

    @Test
    void 사진에서_읽은_글자에_금지어_전화번호_링크가_있으면_422이고_저장도_알림도_없다() throws Exception {
        long notificationsBefore = notifications.count();
        String[][] cases = {
                {"오늘도 힘내세요 씨발", "FILTER_422_001"},
                {"연락 주세요 010-1234-5678", "FILTER_422_002"},
                {"여기로 오세요 www.example.com", "FILTER_422_003"}};
        for (String[] each : cases) {
            when(ocrClient.extractText(any(), any())).thenReturn(each[0]);

            sendLetter(worry.getId()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(each[1]));
        }

        assertThat(replies.existsByWorryIdAndSeniorId(worry.getId(), senior.getId())).isFalse();
        assertThat(notifications.count()).isEqualTo(notificationsBefore);
    }

    @Test
    void 막힌_뒤_문제를_고친_사진으로_다시_보내면_통과한다() throws Exception {
        when(ocrClient.extractText(any(), any())).thenReturn("전화 010-1234-5678");
        sendLetter(worry.getId()).andExpect(status().isUnprocessableEntity());

        when(ocrClient.extractText(any(), any())).thenReturn("고생했어요");
        sendLetter(worry.getId()).andExpect(status().isCreated());
    }

    // ---------- OCR을 못 쓸 때는 막지 않는다 ----------

    @Test
    void OCR이_실패해도_답장은_저장된다() throws Exception {
        when(ocrClient.extractText(any(), any())).thenThrow(new IllegalStateException("OCR 장애"));

        sendLetter(worry.getId()).andExpect(status().isCreated());

        assertThat(replies.existsByWorryIdAndSeniorId(worry.getId(), senior.getId())).isTrue();
    }

    @Test
    void 키가_설정되지_않았으면_OCR을_부르지_않고_저장된다() throws Exception {
        when(ocrClient.isConfigured()).thenReturn(false);

        sendLetter(worry.getId()).andExpect(status().isCreated());

        verify(ocrClient, never()).extractText(any(), any());
    }

    // ---------- 검사 순서와 호출 절약 ----------

    @Test
    void 글과_음성_답장에는_OCR을_부르지_않는다() throws Exception {
        send(senior, worry.getId(), "{\"type\":\"TEXT\",\"content\":\"고생했어요\"}").andExpect(status().isCreated());
        Worry other = worries.saveAndFlush(Worry.of(youth, "다른 고민", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        send(senior, other.getId(), "{\"type\":\"VOICE\",\"content\":\"말로 했어요\"}").andExpect(status().isCreated());

        verify(ocrClient, never()).extractText(any(), any());
    }

    @Test
    void 없는_고민_제재_중_이미_답장한_고민은_OCR을_부르기_전에_404_403_409로_돌려보낸다() throws Exception {
        // 사진 속에 문제가 있어도(422가 될 글자여도) 먼저 확인할 것이 있으면 그 오류가 나오고 OCR은 부르지 않는다.
        when(ocrClient.extractText(any(), any())).thenReturn("연락 주세요 010-1234-5678");

        sendLetter(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));

        Worry hidden = worries.saveAndFlush(Worry.of(youth, "숨길 고민", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        hidden.hide();
        worries.saveAndFlush(hidden);
        sendLetter(hidden.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORRY_404_001"));

        Worry replied = worries.saveAndFlush(Worry.of(youth, "이미 답장", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        replies.saveAndFlush(Reply.text(replied, senior, "이전 답장"));
        sendLetter(replied.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPLY_409_001"));

        senior.applySanction(SanctionLevel.BANNED, null);
        users.saveAndFlush(senior);
        sendLetter(worry.getId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REPLY_403_002"));

        verify(ocrClient, never()).extractText(any(), any());
    }

    @Test
    void 저장되지_않은_사진_주소는_OCR을_부르기_전에_400이다() throws Exception {
        send(senior, worry.getId(),
                "{\"type\":\"LETTER\",\"imageUrl\":\"/files/letters/00000000-0000-4000-8000-000000000000.png\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(ocrClient, never()).extractText(any(), any());
    }
}
