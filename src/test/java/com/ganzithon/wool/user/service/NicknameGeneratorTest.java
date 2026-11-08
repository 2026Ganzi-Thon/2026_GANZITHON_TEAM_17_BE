package com.ganzithon.wool.user.service;

import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NicknameGeneratorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final NicknameGenerator generator = new NicknameGenerator(userRepository);

    @Test
    void 형용사_양_숫자_형식이다() {
        when(userRepository.existsByNickname(anyString())).thenReturn(false);

        String nickname = generator.generate();

        assertThat(nickname).matches("[가-힣]+ 양 [1-9]\\d{2}");
        assertThat(NicknameGenerator.ADJECTIVES).contains(nickname.split(" ")[0]);
    }

    @Test
    void 가능한_모든_닉네임이_2자_이상_10자_이하이다() {
        for (String adjective : NicknameGenerator.ADJECTIVES) {
            for (int number : new int[]{NicknameGenerator.MIN_NUMBER, NicknameGenerator.MAX_NUMBER}) {
                String nickname = adjective + " 양 " + number;
                assertThat(nickname.length()).as(nickname).isBetween(2, 10);
            }
        }
    }

    @Test
    void 형용사_목록에_중복이_없고_모두_3자_이하이다() {
        Set<String> unique = new HashSet<>(NicknameGenerator.ADJECTIVES);

        assertThat(unique).hasSameSizeAs(NicknameGenerator.ADJECTIVES);
        assertThat(NicknameGenerator.ADJECTIVES).allSatisfy(adjective -> assertThat(adjective.length()).isBetween(2, 3));
    }

    @Test
    void 모든_형용사로_만든_닉네임이_금지어_검사를_통과한다() {
        ContentFilter contentFilter = new ContentFilter();
        for (String adjective : NicknameGenerator.ADJECTIVES) {
            for (int number : new int[]{NicknameGenerator.MIN_NUMBER, 555, NicknameGenerator.MAX_NUMBER}) {
                String nickname = adjective + " 양 " + number;
                assertThatCode(() -> contentFilter.validateNickname(nickname)).as(nickname).doesNotThrowAnyException();
            }
        }
    }

    @Test
    void 숫자는_100에서_999_사이이다() {
        when(userRepository.existsByNickname(anyString())).thenReturn(false);

        for (int i = 0; i < 2000; i++) {
            int number = Integer.parseInt(generator.generate().split(" ")[2]);
            assertThat(number).isBetween(NicknameGenerator.MIN_NUMBER, NicknameGenerator.MAX_NUMBER);
        }
    }

    @Test
    void 이미_있는_닉네임이면_다른_닉네임을_다시_만든다() {
        // 처음 두 번은 이미 있는 닉네임, 세 번째에 비어 있는 닉네임을 쓴다.
        when(userRepository.existsByNickname(anyString())).thenReturn(true, true, false);

        String nickname = generator.generate();

        assertThat(nickname).isNotBlank();
        verify(userRepository, times(3)).existsByNickname(anyString());
    }

    @Test
    void 계속_겹치면_예외로_알린다() {
        when(userRepository.existsByNickname(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
        verify(userRepository, times(NicknameGenerator.MAX_ATTEMPTS)).existsByNickname(anyString());
    }

    @Test
    void 난수를_고정하면_같은_닉네임이_나온다() {
        RandomGenerator fixed = new java.util.Random(42);
        NicknameGenerator first = new NicknameGenerator(userRepository, fixed);
        NicknameGenerator second = new NicknameGenerator(userRepository, new java.util.Random(42));

        assertThat(first.randomNickname()).isEqualTo(second.randomNickname());
    }
}
