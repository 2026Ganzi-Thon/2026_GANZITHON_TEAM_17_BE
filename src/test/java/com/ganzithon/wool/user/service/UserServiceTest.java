package com.ganzithon.wool.user.service;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.user.exception.UserErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

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
    private final NicknameValidator nicknameValidator = mock(NicknameValidator.class);
    private final UserService userService = new UserService(userRepository, nicknameGenerator, nicknameValidator);

    @Test
    void 닉네임_추천은_생성기의_결과를_그대로_돌려주고_저장하지_않는다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");

        String suggested = userService.suggestNickname();

        assertThat(suggested).isEqualTo("포근한 양 312");
        verify(userRepository, times(0)).saveAndFlush(any(User.class));
    }

    @Test
    void 가입하면_역할과_나이대와_만든_닉네임으로_저장한다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.join(Role.SENIOR, AgeGroup.AGE_70S_PLUS, null);

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

        User user = userService.join(Role.YOUTH, AgeGroup.AGE_20S, null);

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

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, null)).isSameAs(failure);

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(nicknameGenerator, times(1)).generate();
    }

    @Test
    void 계속_닉네임이_겹치면_다섯_번째에서_예외를_그대로_올린다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        when(userRepository.existsByNickname("포근한 양 312")).thenReturn(true);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("닉네임 UNIQUE 위반");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, null)).isSameAs(failure);

        verify(userRepository, times(5)).saveAndFlush(any(User.class));
    }

    @Test
    void 닉네임_생성기가_실패하면_저장하지_않고_예외를_올린다() {
        when(nicknameGenerator.generate()).thenThrow(new IllegalStateException("닉네임을 만들지 못했습니다."));

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, null)).isInstanceOf(IllegalStateException.class);

        verify(userRepository, times(0)).saveAndFlush(any(User.class));
    }

    // ---------- 사용자가 닉네임을 직접 정한 경우 ----------

    @Test
    void 닉네임을_받으면_검사한_닉네임으로_저장하고_생성기는_부르지_않는다() {
        when(nicknameValidator.validate("  내 닉네임  ")).thenReturn("내 닉네임");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.join(Role.YOUTH, AgeGroup.AGE_30S, "  내 닉네임  ");

        assertThat(user.getNickname()).isEqualTo("내 닉네임");
        assertThat(user.getRole()).isEqualTo(Role.YOUTH);
        assertThat(user.getAgeGroup()).isEqualTo(AgeGroup.AGE_30S);
        verify(nicknameGenerator, times(0)).generate();
    }

    @Test
    void 닉네임_검사에서_걸리면_저장하지_않고_그_예외를_올린다() {
        BusinessException rejected = new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
        when(nicknameValidator.validate("겹치는이름")).thenThrow(rejected);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "겹치는이름")).isSameAs(rejected);

        verify(userRepository, times(0)).saveAndFlush(any(User.class));
    }

    @Test
    void 확인과_저장_사이에_같은_닉네임이_먼저_저장되면_409로_알리고_다시_시도하지_않는다() {
        when(nicknameValidator.validate("내 닉네임")).thenReturn("내 닉네임");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("UNIQUE 위반"));
        when(userRepository.existsByNickname("내 닉네임")).thenReturn(true);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "내 닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(nicknameGenerator, times(0)).generate();
    }

    @Test
    void 닉네임과_무관한_무결성_오류는_409로_바꾸지_않고_그대로_올린다() {
        when(nicknameValidator.validate("내 닉네임")).thenReturn("내 닉네임");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("NOT NULL 위반");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);
        when(userRepository.existsByNickname("내 닉네임")).thenReturn(false);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "내 닉네임")).isSameAs(failure);

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void 닉네임을_받지_않으면_검증기를_부르지_않는다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.join(Role.SENIOR, AgeGroup.AGE_60S, null);

        verify(nicknameValidator, times(0)).validate(any());
    }

    // ---------- 저장 실패의 원인을 가르는 방법 (Hibernate가 알려 주는 제약 종류) ----------

    private static DataIntegrityViolationException violationOf(ConstraintViolationException.ConstraintKind kind) {
        return new DataIntegrityViolationException("무결성 위반",
                new ConstraintViolationException("제약 위반", new SQLException("db"), "insert", kind, "constraint"));
    }

    @Test
    void 저장_실패가_NOT_NULL_위반이면_같은_닉네임이_있어도_409로_바꾸지_않는다() {
        when(nicknameValidator.validate("내 닉네임")).thenReturn("내 닉네임");
        DataIntegrityViolationException failure = violationOf(ConstraintViolationException.ConstraintKind.NOT_NULL);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);
        // 다른 요청이 같은 닉네임을 저장해 둔 가장 나쁜 경우
        when(userRepository.existsByNickname("내 닉네임")).thenReturn(true);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "내 닉네임")).isSameAs(failure);
    }

    @Test
    void 저장_실패가_외래_키_위반이어도_409로_바꾸지_않는다() {
        when(nicknameValidator.validate("내 닉네임")).thenReturn("내 닉네임");
        DataIntegrityViolationException failure = violationOf(ConstraintViolationException.ConstraintKind.FOREIGN_KEY);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);
        when(userRepository.existsByNickname("내 닉네임")).thenReturn(true);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "내 닉네임")).isSameAs(failure);
    }

    @Test
    void 저장_실패가_UNIQUE_위반이고_그_닉네임이_있으면_409이다() {
        when(nicknameValidator.validate("내 닉네임")).thenReturn("내 닉네임");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(violationOf(ConstraintViolationException.ConstraintKind.UNIQUE));
        when(userRepository.existsByNickname("내 닉네임")).thenReturn(true);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "내 닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    void 저장_실패가_UNIQUE_위반이어도_그_닉네임이_없으면_원래_예외를_올린다() {
        when(nicknameValidator.validate("내 닉네임")).thenReturn("내 닉네임");
        DataIntegrityViolationException failure = violationOf(ConstraintViolationException.ConstraintKind.UNIQUE);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);
        when(userRepository.existsByNickname("내 닉네임")).thenReturn(false);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, "내 닉네임")).isSameAs(failure);
    }

    @Test
    void 서버가_만든_닉네임도_NOT_NULL_위반이면_다시_시도하지_않고_바로_올린다() {
        when(nicknameGenerator.generate()).thenReturn("포근한 양 312");
        DataIntegrityViolationException failure = violationOf(ConstraintViolationException.ConstraintKind.NOT_NULL);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);
        when(userRepository.existsByNickname("포근한 양 312")).thenReturn(true);

        assertThatThrownBy(() -> userService.join(Role.YOUTH, AgeGroup.AGE_20S, null)).isSameAs(failure);

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }
}
