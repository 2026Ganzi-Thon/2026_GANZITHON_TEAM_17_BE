package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 DB(H2)로 닉네임 수정을 검증한다. 테스트끼리 닉네임이 겹치지 않도록 매번 새 이름을 쓴다.
@AutoConfigureMockMvc
class NicknameUpdateControllerTest extends IntegrationTestSupport {

    private static final String URL = "/api/users/me/nickname";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 5);
    }

    private User newUser(Role role) {
        return userRepository.saveAndFlush(User.join(role, AgeGroup.AGE_30S, unique("이름")));
    }

    private ResultActions patchNickname(User user, String body) throws Exception {
        return mockMvc.perform(patch(URL).header("X-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String nicknameOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getNickname();
    }

    private static String body(String nicknameJson) {
        return "{\"nickname\":" + nicknameJson + "}";
    }

    // ---------- 성공 ----------

    @Test
    void 닉네임을_바꾸면_200과_바뀐_닉네임을_래퍼로_내려주고_저장된다() throws Exception {
        User user = newUser(Role.YOUTH);
        String wanted = unique("봄볕");

        patchNickname(user, body("\"" + wanted + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("USER_200_1"))
                .andExpect(jsonPath("$.message").value("닉네임을 바꿨습니다."))
                .andExpect(jsonPath("$.data.nickname").value(wanted))
                .andExpect(jsonPath("$.data.length()").value(1));

        assertThat(nicknameOf(user)).isEqualTo(wanted);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 10})
    void 길이가_2자와_10자인_닉네임은_성공이다(int length) throws Exception {
        User user = newUser(Role.YOUTH);
        // 무작위 16진수 문자로 정확히 그 길이의 닉네임을 만든다. (다른 테스트의 닉네임은 이보다 길어 겹치지 않는다)
        String nickname = UUID.randomUUID().toString().replace("-", "").substring(0, length);

        patchNickname(user, body("\"" + nickname + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname));

        assertThat(nicknameOf(user)).isEqualTo(nickname);
    }

    @Test
    void 어르신도_바꿀_수_있다() throws Exception {
        User senior = newUser(Role.SENIOR);
        String wanted = unique("선배");

        patchNickname(senior, body("\"" + wanted + "\"")).andExpect(status().isOk());

        assertThat(nicknameOf(senior)).isEqualTo(wanted);
    }

    @Test
    void 바꾼_뒤_내_정보_조회에도_새_닉네임이_보인다() throws Exception {
        User user = newUser(Role.YOUTH);
        String wanted = unique("새이름");

        patchNickname(user, body("\"" + wanted + "\"")).andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("X-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(wanted));
    }

    @Test
    void 앞뒤_공백은_잘라서_저장한다() throws Exception {
        User user = newUser(Role.YOUTH);
        String wanted = unique("공백");

        patchNickname(user, body("\"  " + wanted + "  \""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(wanted));

        assertThat(nicknameOf(user)).isEqualTo(wanted);
    }

    @Test
    void 지금_닉네임과_같은_값을_보내면_오류가_아니라_성공이다() throws Exception {
        User user = newUser(Role.YOUTH);
        String current = nicknameOf(user);

        patchNickname(user, body("\"" + current + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(current));

        assertThat(nicknameOf(user)).isEqualTo(current);
    }

    @Test
    void 대소문자만_바꾸는_것도_성공이다() throws Exception {
        User user = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "Sunny" + UUID.randomUUID().toString().substring(0, 4)));
        String lower = user.getNickname().toLowerCase();

        patchNickname(user, body("\"" + lower + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(lower));

        assertThat(nicknameOf(user)).isEqualTo(lower);
    }

    @Test
    void 다른_사람의_닉네임은_바뀌지_않는다() throws Exception {
        User me = newUser(Role.YOUTH);
        User other = newUser(Role.SENIOR);
        String otherNickname = nicknameOf(other);

        patchNickname(me, body("\"" + unique("내것") + "\"")).andExpect(status().isOk());

        assertThat(nicknameOf(other)).isEqualTo(otherNickname);
    }

    // ---------- 인증 401 ----------

    @Test
    void 헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body("\"가나다\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    @Test
    void 없는_사용자_ID면_401이다() throws Exception {
        mockMvc.perform(patch(URL).header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"가나다\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    @Test
    void 인증이_안_되면_본문이_틀려도_401이_먼저다() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("이건 JSON이 아니에요"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    // ---------- 형식 400 ----------

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "\"가\"", "\"가나다라마바사아자차카\"", "\"가나\\n다라\"", "\"\\u200B가나\"", "\"\\u3164\\u3164\"", "null"})
    void 닉네임_형식이_맞지_않으면_400이고_바뀌지_않는다(String nicknameJson) throws Exception {
        User user = newUser(Role.YOUTH);
        String before = nicknameOf(user);

        patchNickname(user, body(nicknameJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        assertThat(nicknameOf(user)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "이건 JSON이 아니에요"})
    void 필드가_없거나_본문이_올바르지_않으면_400이다(String rawBody) throws Exception {
        User user = newUser(Role.YOUTH);

        patchNickname(user, rawBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "true", "1.5", "[]", "{}", "[\"가나다\"]"})
    void 닉네임이_문자열이_아니면_400이다(String nicknameJson) throws Exception {
        User user = newUser(Role.YOUTH);

        patchNickname(user, body(nicknameJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    // ---------- 금지어 422, 중복 409 ----------

    @Test
    void 금지어가_있으면_422이고_바뀌지_않는다() throws Exception {
        User user = newUser(Role.YOUTH);
        String before = nicknameOf(user);

        patchNickname(user, body("\"씨발이\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILTER_422_001"))
                .andExpect(jsonPath("$.message").value("사용할 수 없는 표현이 포함되어 있어요."));

        assertThat(nicknameOf(user)).isEqualTo(before);
    }

    @Test
    void 다른_사람이_쓰는_닉네임이면_409이고_바뀌지_않는다() throws Exception {
        User me = newUser(Role.YOUTH);
        User other = newUser(Role.SENIOR);
        String before = nicknameOf(me);

        patchNickname(me, body("\"" + nicknameOf(other) + "\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("USER_409_001"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));

        assertThat(nicknameOf(me)).isEqualTo(before);
    }

    @Test
    void 금지어와_중복이_함께_있으면_금지어_오류가_먼저다() throws Exception {
        User me = newUser(Role.YOUTH);
        userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "씨발이"));

        patchNickname(me, body("\"씨발이\"")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 한_번_실패한_뒤에도_다시_바꿀_수_있다() throws Exception {
        User me = newUser(Role.YOUTH);
        User other = newUser(Role.SENIOR);
        patchNickname(me, body("\"" + nicknameOf(other) + "\"")).andExpect(status().isConflict());
        String wanted = unique("다시");

        patchNickname(me, body("\"" + wanted + "\"")).andExpect(status().isOk());

        assertThat(nicknameOf(me)).isEqualTo(wanted);
    }
}
