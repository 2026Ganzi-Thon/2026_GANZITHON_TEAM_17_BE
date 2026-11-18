package com.ganzithon.wool.upload.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LetterImageUrlTest {

    @Test
    void 업로드가_만드는_형태의_주소는_통과한다() {
        for (String extension : new String[]{"jpg", "png"}) {
            assertThat(LetterImageUrl.isValid("/files/letters/" + UUID.randomUUID() + "." + extension)).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "https://example.com/a.png",
            "/files/letters/",
            "/files/letters/3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10",
            "/files/letters/3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.jpeg",
            // 클로바 OCR이 읽지 못하는 형식이라 받지 않는다.
            "/files/letters/3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.webp",
            "/files/letters/3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.png/",
            "/files/letters/3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.png\n",
            "/files/letters/../3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.png",
            "//files/letters/3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.png"
    })
    void 그_밖의_주소는_거부한다(String url) {
        assertThat(LetterImageUrl.isValid(url)).isFalse();
    }
}
