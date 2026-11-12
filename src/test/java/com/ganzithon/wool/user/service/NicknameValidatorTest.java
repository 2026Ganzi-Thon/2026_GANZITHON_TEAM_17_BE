package com.ganzithon.wool.user.service;

import com.ganzithon.wool.filter.exception.FilterErrorCode;
import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.global.exception.ErrorCode;
import com.ganzithon.wool.user.exception.UserErrorCode;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NicknameValidatorTest {

    private static final String EMOJI = "💌";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final NicknameValidator validator = new NicknameValidator(new ContentFilter(), userRepository);

    private void assertRejectedWith(String raw, ErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ---------- 통과 ----------

    @Test
    void 형식이_맞고_금지어가_없고_겹치지_않으면_그대로_돌려준다() {
        when(userRepository.existsByNickname("봄볕 양 7")).thenReturn(false);

        assertThat(validator.validate("봄볕 양 7")).isEqualTo("봄볕 양 7");
    }

    @Test
    void 앞뒤_공백은_잘라서_돌려주고_중간_공백은_그대로_둔다() {
        when(userRepository.existsByNickname("포근한 양 312")).thenReturn(false);

        assertThat(validator.validate("  포근한 양 312\t ")).isEqualTo("포근한 양 312");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차", "ab", "1234567890", "a b"})
    void 길이가_2자에서_10자_사이면_통과한다(String raw) {
        when(userRepository.existsByNickname(anyString())).thenReturn(false);

        assertThat(validator.validate(raw)).isEqualTo(raw);
    }

    @Test
    void 이모지는_한_글자로_세어_10자까지_허용한다() {
        when(userRepository.existsByNickname(anyString())).thenReturn(false);

        assertThat(EMOJI.length()).isEqualTo(2);
        assertThat(validator.validate(EMOJI.repeat(10))).isEqualTo(EMOJI.repeat(10));
    }

    // ---------- 형식 오류 400 ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "가", " 가 ", "가나다라마바사아자차카", "12345678901"})
    void 비어_있거나_공백뿐이거나_길이가_맞지_않으면_400이다(String raw) {
        assertRejectedWith(raw, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 이모지_11개는_길이_초과로_400이다() {
        assertRejectedWith(EMOJI.repeat(11), CommonErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나\n다라", "가나\t다라", "가나\u0000다라"})
    void 줄바꿈과_탭_같은_제어_문자가_들어_있으면_400이다(String raw) {
        assertRejectedWith(raw, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 형식이_틀리면_금지어_검사와_중복_조회를_하지_않는다() {
        assertRejectedWith("가", CommonErrorCode.INVALID_INPUT);

        verify(userRepository, never()).existsByNickname(anyString());
    }

    // ---------- 공백과 보이지 않는 문자 ----------

    @Test
    void 앞뒤의_유니코드_공백도_잘라낸다() {
        when(userRepository.existsByNickname("봄볕")).thenReturn(false);

        // NBSP(U+00A0), 전각 공백(U+3000), 가는 공백(U+2009)은 String.strip()이 남기는 공백이다.
        assertThat(validator.validate(" 　봄볕  ")).isEqualTo("봄볕");
    }

    @ParameterizedTest
    @ValueSource(strings = {"  ", "　　　", "  "})
    void 유니코드_공백만_있으면_빈_닉네임으로_보고_400이다(String raw) {
        assertRejectedWith(raw, CommonErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가 나", "가　나", "가 나"})
    void 닉네임_안의_일반_공백이_아닌_공백은_400이다(String raw) {
        assertRejectedWith(raw, CommonErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가​나", "가‮나", "가﻿나", "가­나", "가⁠나", "​가나"})
    void 보이지_않는_서식_문자가_있으면_400이다(String raw) {
        // 제로폭 공백, 방향 제어, BOM, 소프트 하이픈, 단어 결합자
        assertRejectedWith(raw, CommonErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ㅤㅤㅤ", "가ㅤ나", "ᅟᅠ", "가ﾠ나", "⠀⠀⠀"})
    void 화면에_그려지지_않는_채움_문자가_있으면_400이다(String raw) {
        // 한글 채움 문자(U+3164 등)와 점자 빈칸(U+2800)은 빈 닉네임처럼 보이게 만든다.
        assertRejectedWith(raw, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 이모지를_잇는_결합자는_허용하고_코드포인트로_길이를_센다() {
        when(userRepository.existsByNickname(anyString())).thenReturn(false);
        String family = "👨‍👩‍👧‍👦";   // 가족 이모지 (코드포인트 7개)

        assertThat(family.codePointCount(0, family.length())).isEqualTo(7);
        assertThat(validator.validate(family)).isEqualTo(family);
        // 7개짜리 하나와 이어 붙인 3개를 합쳐 10개를 넘으면 거부한다.
        assertRejectedWith(family + "가나나나", CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 닉네임_중간의_일반_공백은_허용한다() {
        when(userRepository.existsByNickname("가 나")).thenReturn(false);

        assertThat(validator.validate("가 나")).isEqualTo("가 나");
    }


    // ---------- 닉네임 수정: 자기 자신은 중복으로 보지 않는다 ----------

    @Test
    void 가입한_사용자가_검사하면_자기_자신을_뺀_중복_조회를_쓴다() {
        when(userRepository.existsByNicknameAndIdNot("새이름", "user-1")).thenReturn(false);

        assertThat(validator.validate("새이름", "user-1")).isEqualTo("새이름");

        verify(userRepository, never()).existsByNickname(anyString());
    }

    @Test
    void 다른_사람이_쓰는_닉네임이면_409이다() {
        when(userRepository.existsByNicknameAndIdNot("남의이름", "user-1")).thenReturn(true);

        assertThatThrownBy(() -> validator.validate("남의이름", "user-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    void 사용자_ID가_없으면_기존처럼_전체에서_중복을_조회한다() {
        when(userRepository.existsByNickname("새이름")).thenReturn(false);

        assertThat(validator.validate("새이름", null)).isEqualTo("새이름");

        verify(userRepository, never()).existsByNicknameAndIdNot(anyString(), anyString());
    }

    @Test
    void 사용자가_있어도_형식과_금지어_검사는_그대로_한다() {
        assertRejectedWith("가", CommonErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> validator.validate("씨발이", "user-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FilterErrorCode.BANNED_WORD));
        verify(userRepository, never()).existsByNicknameAndIdNot(anyString(), anyString());
    }

    // ---------- 금지어 422 ----------

    @ParameterizedTest
    @ValueSource(strings = {"씨발이", "씨 발", "개새끼들"})
    void 금지어가_있으면_422_금지어_오류다(String raw) {
        assertRejectedWith(raw, FilterErrorCode.BANNED_WORD);
    }

    @Test
    void 금지어면_중복_조회를_하지_않는다() {
        assertRejectedWith("씨발이", FilterErrorCode.BANNED_WORD);

        verify(userRepository, never()).existsByNickname(anyString());
    }

    // ---------- 중복 409 ----------

    @Test
    void 이미_사용_중이면_409이다() {
        when(userRepository.existsByNickname("겹치는이름")).thenReturn(true);

        assertRejectedWith("겹치는이름", UserErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    void 공백을_자른_뒤의_닉네임으로_중복을_확인한다() {
        when(userRepository.existsByNickname("겹치는이름")).thenReturn(true);

        assertRejectedWith("  겹치는이름  ", UserErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    void 에러_코드는_명세와_같다() {
        assertThat(UserErrorCode.NICKNAME_DUPLICATED.getCode()).isEqualTo("USER_409_001");
        assertThat(UserErrorCode.NICKNAME_DUPLICATED.getMessage()).isEqualTo("이미 사용 중인 닉네임입니다.");
        assertThat(UserErrorCode.NICKNAME_DUPLICATED.getHttpStatus().value()).isEqualTo(409);
    }
}
