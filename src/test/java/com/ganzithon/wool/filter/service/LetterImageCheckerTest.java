package com.ganzithon.wool.filter.service;

import com.ganzithon.wool.filter.client.OcrClient;
import com.ganzithon.wool.filter.exception.FilterErrorCode;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.storage.LocalFileStorage;
import com.ganzithon.wool.global.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 손편지 사진에서 읽은 글자에 글 답장과 같은 검사를 하고, OCR을 못 쓰는 상황에서는 검사를 건너뛴다.
class LetterImageCheckerTest {

    @TempDir
    Path root;

    private LocalFileStorage storage;
    private FakeOcr ocr;
    private LetterImageChecker checker;
    private String url;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new StorageProperties(root.toString()));
        ocr = new FakeOcr();
        checker = new LetterImageChecker(storage, ocr, new ContentFilter());
        url = storage.save("letters", "3f2b8c1e-5a47-4e0b-9c1d-7a6e2f4b9d10.png", new byte[]{1, 2, 3});
    }

    private void assertRejected(String text, FilterErrorCode expected) {
        ocr.answer = image -> text;
        assertThatThrownBy(() -> checker.check(url))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void 읽은_글자에_금지어가_있으면_글_답장과_같은_422이다() {
        assertRejected("오늘도 힘내세요 씨발", FilterErrorCode.BANNED_WORD);
    }

    @Test
    void 읽은_글자에_전화번호가_있으면_422이다() {
        assertRejected("전화 주세요\n010-1234-5678", FilterErrorCode.PHONE_NUMBER);
    }

    @Test
    void 읽은_글자에_링크나_외부_연락처가_있으면_422이다() {
        assertRejected("여기로 오세요 www.example.com", FilterErrorCode.EXTERNAL_CONTACT);
        assertRejected("카톡 아이디 abc123", FilterErrorCode.EXTERNAL_CONTACT);
    }

    @Test
    void 문제가_없는_글자나_빈_글자는_통과한다() {
        ocr.answer = image -> "힘든 일이 많았지요. 그래도 잘 버텨 주어서 고맙습니다.";
        assertThatCode(() -> checker.check(url)).doesNotThrowAnyException();

        ocr.answer = image -> "";
        assertThatCode(() -> checker.check(url)).doesNotThrowAnyException();
    }

    @Test
    void 저장된_사진과_형식을_OCR에_그대로_넘긴다() {
        ocr.answer = image -> "";

        checker.check(url);

        assertThat(ocr.images).hasSize(1);
        assertThat(ocr.images.get(0)).containsExactly(1, 2, 3);
        assertThat(ocr.formats).containsExactly("png");
    }

    @Test
    void OCR이_실패해도_답장을_막지_않고_검사를_건너뛴다() {
        ocr.answer = image -> {
            throw new IllegalStateException("OCR 장애");
        };

        assertThatCode(() -> checker.check(url)).doesNotThrowAnyException();
        assertThat(ocr.calls).isEqualTo(1);
    }

    @Test
    void 키가_설정되지_않았으면_OCR을_부르지_않는다() {
        ocr.configured = false;

        assertThatCode(() -> checker.check(url)).doesNotThrowAnyException();
        assertThat(ocr.calls).isZero();
    }

    @Test
    void 저장된_파일을_못_찾으면_OCR을_부르지_않는다() {
        assertThatCode(() -> checker.check("/files/letters/00000000-0000-4000-8000-000000000000.png")).doesNotThrowAnyException();
        assertThat(ocr.calls).isZero();
    }

    private static class FakeOcr implements OcrClient {
        boolean configured = true;
        Function<byte[], String> answer = image -> "";
        int calls;
        final List<byte[]> images = new ArrayList<>();
        final List<String> formats = new ArrayList<>();

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String extractText(byte[] image, String format) {
            calls++;
            images.add(image);
            formats.add(format);
            return answer.apply(image);
        }
    }
}
