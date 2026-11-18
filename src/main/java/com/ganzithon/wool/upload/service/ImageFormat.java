package com.ganzithon.wool.upload.service;

import java.util.Optional;

// 허용하는 사진 형식. 파일 이름이나 Content-Type은 속이기 쉬워서 파일 맨 앞의 고유 바이트(시그니처)로 판단한다.
// 손편지 내용 검사에 쓰는 클로바 OCR이 jpg/png만 읽으므로 webp는 받지 않는다. (받으면 검사를 피해 갈 수 있다)
public enum ImageFormat {
    JPEG("jpg"),
    PNG("png");

    private final String extension;

    ImageFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
