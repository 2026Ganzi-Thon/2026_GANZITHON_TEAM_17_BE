package com.ganzithon.wool.global.auth;

import com.ganzithon.wool.global.exception.GlobalExceptionHandler;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginUserArgumentResolverTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final User senior = User.join(Role.SENIOR, AgeGroup.AGE_60S, "포근한 양 312");
    private final User youth = User.join(Role.YOUTH, AgeGroup.AGE_20S, "따뜻한 양 7");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(userRepository.findById(senior.getId())).thenReturn(Optional.of(senior));
        when(userRepository.findById(youth.getId())).thenReturn(Optional.of(youth));
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setCustomArgumentResolvers(new LoginUserArgumentResolver(userRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/any"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    @Test
    void 등록되지_않은_사용자는_401이다() throws Exception {
        mockMvc.perform(get("/any").header("X-User-Id", "unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    @Test
    void 역할_제한이_없으면_누구나_통과한다() throws Exception {
        mockMvc.perform(get("/any").header("X-User-Id", youth.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(youth.getNickname()));
    }

    @Test
    void 어르신_전용_API에_청년이_접근하면_403이다() throws Exception {
        mockMvc.perform(get("/senior").header("X-User-Id", youth.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_403_001"));
    }

    @Test
    void 어르신_전용_API에_어르신은_통과한다() throws Exception {
        mockMvc.perform(get("/senior").header("X-User-Id", senior.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(senior.getNickname()));
    }

    @RestController
    static class TestController {

        @GetMapping(value = "/any", produces = "text/plain;charset=UTF-8")
        String any(@LoginUser User user) {
            return user.getNickname();
        }

        @GetMapping(value = "/senior", produces = "text/plain;charset=UTF-8")
        String senior(@LoginUser(Role.SENIOR) User user) {
            return user.getNickname();
        }
    }
}
