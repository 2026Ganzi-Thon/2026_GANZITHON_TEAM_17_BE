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
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "확인(검증)을 통과한 뒤 저장하는 순간 같은 닉네임이 이미 있는" 경합 상황을 실제 DB의 UNIQUE 위반으로 재현한다.
// 검증기의 중복 확인만 건너뛰게 해서, 두 요청이 동시에 확인을 통과한 것과 같은 상태를 만든다. (실제 DB에서 일어나는 예외 경로를 그대로 지난다)
// 테스트 DB는 H2이므로 MySQL에서의 동작까지 보장하지는 않지만, 예외가 어떤 모양으로 올라오는지와
// 웹 요청의 트랜잭션·영속성 컨텍스트 처리가 실패 뒤에도 정상인지를 확인한다.
@AutoConfigureMockMvc
class NicknameConflictIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private NicknameValidator nicknameValidator;

    private String existingNickname;

    @BeforeEach
    void setUp() {
        // 다른 테스트와 겹치지 않도록 매번 새 닉네임을 쓴다.
        existingNickname = "경합" + UUID.randomUUID().toString().substring(0, 6);
        userRepository.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, existingNickname));
        // 검증기의 형식·금지어·중복 확인을 건너뛰고 입력을 그대로 통과시킨다.
        doAnswer(invocation -> invocation.getArgument(0)).when(nicknameValidator).validate(anyString());
    }

    private String body(String nickname) {
        return "{\"role\":\"YOUTH\",\"ageGroup\":\"20S\",\"nickname\":\"" + nickname + "\"}";
    }

    @Test
    void 저장_때_UNIQUE_위반이_나면_실제_예외에_UNIQUE_제약_종류가_담겨_있다() {
        // 이 판별이 서비스의 409 변환에 쓰인다. DB가 알려 주는 예외의 모양이 바뀌면 이 테스트가 먼저 깨진다.
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> userRepository.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, existingNickname)));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        ConstraintViolationException violation = null;
        for (Throwable cause = thrown; cause != null && violation == null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException found) {
                violation = found;
            }
        }
        assertThat(violation).as("Hibernate ConstraintViolationException이 원인 체인에 있어야 한다").isNotNull();
        assertThat(violation.getKind()).isEqualTo(ConstraintViolationException.ConstraintKind.UNIQUE);
    }

    @Test
    void 서비스는_저장_때의_UNIQUE_위반을_409로_바꾼다() {
        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, existingNickname))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    void 웹_요청에서도_409이고_실패_뒤의_요청이_정상으로_처리된다() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body(existingNickname)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_409_001"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
        assertThat(userRepository.count()).isEqualTo(before);

        // 실패가 DB 연결이나 영속성 컨텍스트를 망가뜨리지 않았는지: 새 닉네임 가입과 같은 닉네임 재시도가 모두 정상이어야 한다.
        String freshNickname = "정상" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body(freshNickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nickname").value(freshNickname));
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body(existingNickname)))
                .andExpect(status().isConflict());

        assertThat(userRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void 서버가_닉네임을_만드는_가입은_검증기와_상관없이_정상이다() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"YOUTH\",\"ageGroup\":\"20S\"}"))
                .andExpect(status().isCreated());
    }
}
