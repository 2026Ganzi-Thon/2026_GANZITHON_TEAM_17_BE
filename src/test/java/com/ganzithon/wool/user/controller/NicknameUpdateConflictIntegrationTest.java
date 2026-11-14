package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.exception.UserErrorCode;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.user.service.NicknameValidator;
import com.ganzithon.wool.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 이 테스트는 실제 동시 요청이 아니다. "검증(중복 확인)을 통과한 뒤 저장하는 순간 같은 닉네임이 이미 있는" 결과를
// 실제 DB의 UNIQUE 위반으로 만들어, 그 뒤의 예외 처리(409 변환, 닉네임 되돌리기, 실패 뒤 요청 정상 여부)를 검증한다.
// 검증기의 중복 확인만 건너뛰게 해서 그 상태를 만든다. 진짜 동시 실행은 NicknameUpdateConcurrencyTest가 확인한다.
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않는다.
@AutoConfigureMockMvc
class NicknameUpdateConflictIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private NicknameValidator nicknameValidator;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 5);
        me = userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, "내이름" + suffix));
        other = userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "남이름" + suffix));
        // 검증기의 중복 확인을 건너뛰고 입력을 그대로 통과시킨다.
        doAnswer(invocation -> invocation.getArgument(0)).when(nicknameValidator).validate(anyString(), anyString());
    }

    private String nicknameOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getNickname();
    }

    @Test
    void 서비스는_저장_때의_UNIQUE_위반을_409로_바꾸고_닉네임을_되돌린다() {
        String myNickname = me.getNickname();

        assertThatThrownBy(() -> userService.updateNickname(me, other.getNickname()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));

        // 메모리의 객체와 DB 모두 원래 닉네임이다.
        assertThat(me.getNickname()).isEqualTo(myNickname);
        assertThat(nicknameOf(me)).isEqualTo(myNickname);
        assertThat(nicknameOf(other)).isEqualTo(other.getNickname());
    }

    @Test
    void 웹_요청에서도_409이고_실패_뒤의_요청이_정상으로_처리된다() throws Exception {
        String myNickname = me.getNickname();

        mockMvc.perform(patch("/api/users/me/nickname").header("X-User-Id", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + other.getNickname() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_409_001"));
        assertThat(nicknameOf(me)).isEqualTo(myNickname);

        // 실패가 DB 연결이나 영속성 컨텍스트를 망가뜨리지 않았는지 확인: 새 닉네임으로는 정상으로 바뀐다.
        String wanted = "정상" + UUID.randomUUID().toString().substring(0, 5);
        mockMvc.perform(patch("/api/users/me/nickname").header("X-User-Id", me.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + wanted + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(wanted));
        assertThat(nicknameOf(me)).isEqualTo(wanted);
    }
}
