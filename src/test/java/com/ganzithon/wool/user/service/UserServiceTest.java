package com.ganzithon.wool.user.service;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final NicknameGenerator nicknameGenerator = mock(NicknameGenerator.class);
    private final UserService userService = new UserService(userRepository, nicknameGenerator);

    @Test
    void 가입하면_역할과_나이대와_만든_닉네임으로_저장한다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS);

        assertThat(user.getRole()).isEqualTo(Role.SENIOR);
        assertThat(user.getAgeGroup()).isEqualTo(AgeGroup.AGE_70S_PLUS);
        assertThat(user.getNickname()).isEqualTo("포근한 양 312");
        assertThat(user.getSanctionLevel()).isEqualTo(SanctionLevel.NONE);
        assertThat(user.getId()).isNotBlank();
    }

    @Test
    void 저장할_때_닉네임이_겹치면_닉네임을_새로_만들어_다시_저장한다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312", "다정한 양 745");
        // 실패한 뒤 조회하면 그 닉네임이 이미 저장되어 있다. (닉네임 UNIQUE 위반)
        when(userRepository.existsByNickname("포근한 양 312")).thenReturn(true);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("닉네임 UNIQUE 위반"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.join(Role.YOUTH, AgeGroup.AGE_20S);

        assertThat(user.getNickname()).isEqualTo("다정한 양 745");
        verify(nicknameGenerator, times(2)).generate();
        verify(userRepository, times(2)).saveAndFlush(any(User.class));
    }

    @Test
    void 닉네임과_무관한_무결성_오류는_다시_시도하지_않고_바로_올린다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        // 실패한 뒤 조회해도 그 닉네임이 저장되어 있지 않다. (NOT NULL, 길이 초과 등 다른 원인)
        when(userRepository.existsByNickname("포근한 양 312")).thenReturn(false);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("NOT NULL 위반");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S)).isSameAs(failure);

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(nicknameGenerator, times(1)).generate();
    }

    @Test
    void 계속_닉네임이_겹치면_다섯_번째에서_예외를_그대로_올린다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        when(userRepository.existsByNickname("포근한 양 312")).thenReturn(true);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("닉네임 UNIQUE 위반");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S)).isSameAs(failure);

        verify(userRepository, times(5)).saveAndFlush(any(User.class));
    }

    @Test
    void 닉네임_생성기가_실패하면_저장하지_않고_예외를_올린다() {
        when(nicknameGenerator.generate()).thenThrow(new IllegalStateException("닉네임을 만들지 못했습니다."));

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S)).isInstanceOf(IllegalStateException.class);

        verify(userRepository, times(0)).saveAndFlush(any(User.class));
    }
}
