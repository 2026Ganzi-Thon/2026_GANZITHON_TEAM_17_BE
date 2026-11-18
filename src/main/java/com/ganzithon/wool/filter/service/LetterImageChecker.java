package com.ganzithon.wool.filter.service;

import com.ganzithon.wool.filter.client.OcrClient;
import com.ganzithon.wool.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

// 손편지 사진에 적힌 글자를 읽어 글 답장과 같은 검사(금지어, 전화번호, 링크·외부 연락처)를 한다.
// 걸리면 ContentFilter가 BusinessException(422)을 던지고, 사진과 읽은 글자는 고치거나 저장하지 않는다.
//
// OCR은 보조 수단이라 읽지 못해도 답장을 막지 않는다. 키가 없거나, 파일을 못 찾거나, OCR이 실패(장애, 시간 초과,
// 무료 한도 초과 등)하면 검사를 건너뛴다. 이때는 청년의 신고와 어르신 제재가 안전장치다.
// 사진 내용과 읽은 글자는 개인적인 글이라 로그에 남기지 않고, 호출 횟수와 실패 종류만 남긴다.
@Slf4j
@Component
@RequiredArgsConstructor
public class LetterImageChecker {

    private final FileStorage fileStorage;
    private final OcrClient ocrClient;
    private final ContentFilter contentFilter;

    public void check(String imageUrl) {
        if (!ocrClient.isConfigured()) {
            return;
        }
        Optional<byte[]> image = fileStorage.read(imageUrl);
        if (image.isEmpty()) {
            return;
        }
        String text;
        try {
            log.info("event=letter_ocr_call");
            text = ocrClient.extractText(image.get(), formatOf(imageUrl));
        } catch (RuntimeException e) {
            log.warn("event=letter_ocr_failed fallback=skip reason={}", e.getClass().getSimpleName());
            return;
        }
        contentFilter.validateLongText(text);
    }

    // 업로드가 만드는 주소는 항상 .jpg 또는 .png로 끝난다. (ImageFormat)
    private static String formatOf(String imageUrl) {
        return imageUrl.substring(imageUrl.lastIndexOf('.') + 1);
    }
}
