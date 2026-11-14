package com.ganzithon.wool.filter.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    // 수학용 굵은 글자 A(U+1D400)는 보조 평면 문자이면서 글자다. (UTF-16으로는 char 두 개)
    private static final String SUPPLEMENTARY_LETTER = new String(Character.toChars(0x1D400));
    // 이모지는 글자도 숫자도 아니라서 지워진다.
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @Test
    void 공백과_특수문자를_지우고_소문자로_바꾼다() {
        assertThat(TextNormalizer.normalize("씨 발! ABC-123")).isEqualTo("씨발abc123");
    }

    @Test
    void 숫자를_뺀_사본은_글자만_남긴다() {
        assertThat(TextNormalizer.normalizeLettersWithOrigin("씨1발 A2b").text()).isEqualTo("씨발ab");
    }

    @Test
    void 원문은_바뀌지_않는다() {
        String original = "씨 1 발 " + EMOJI;
        String copy = new String(original);

        TextNormalizer.normalize(original);
        TextNormalizer.normalizeWithOrigin(original);
        TextNormalizer.normalizeLettersWithOrigin(original);

        assertThat(original).isEqualTo(copy);
    }

    @Test
    void 원문_위치는_정규화된_글자마다_이어진다() {
        TextNormalizer.Normalized normalized = TextNormalizer.normalizeWithOrigin("a b1");

        assertThat(normalized.text()).isEqualTo("ab1");
        assertThat(normalized.origin()).startsWith(0, 2, 3);
    }

    @Test
    void 숫자를_뺀_사본의_원문_위치는_숫자를_건너뛴다() {
        TextNormalizer.Normalized normalized = TextNormalizer.normalizeLettersWithOrigin("씨12발");

        assertThat(normalized.text()).isEqualTo("씨발");
        assertThat(normalized.origin()).startsWith(0, 3);
    }

    @Test
    void 보조_평면_문자도_세_메서드가_같은_결과를_낸다() {
        String text = "A" + SUPPLEMENTARY_LETTER + EMOJI + "발";

        String normalize = TextNormalizer.normalize(text);
        TextNormalizer.Normalized withOrigin = TextNormalizer.normalizeWithOrigin(text);
        TextNormalizer.Normalized lettersOnly = TextNormalizer.normalizeLettersWithOrigin(text);

        assertThat(normalize).isEqualTo(withOrigin.text()).isEqualTo(lettersOnly.text());
        // 보조 평면 글자(소문자로 바뀐 형태)는 char 두 개를 차지하고, 둘 다 원문의 같은 위치(1)를 가리킨다.
        assertThat(normalize).hasSize(1 + 2 + 1);
        assertThat(withOrigin.origin()[1]).isEqualTo(1);
        assertThat(withOrigin.origin()[2]).isEqualTo(1);
        // 이모지(원문 3~4)는 지워지고, 그 뒤의 "발"은 원문 위치 3 + 2 = 5를 가리킨다.
        assertThat(withOrigin.origin()[3]).isEqualTo(1 + 2 + 2);
    }

    @Test
    void null은_빈_문자열이_된다() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
