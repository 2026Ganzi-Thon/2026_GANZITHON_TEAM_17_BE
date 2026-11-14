package com.ganzithon.wool.upload.service;

import java.util.Optional;

// 허용하는 사진 형식. 파일 이름이나 Content-Type은 속이기 쉬워서 파일 맨 앞의 고유 바이트(시그니처)로 판단한다.
public enum ImageFormat {
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp");

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
        // WebP는 "RIFF" + 파일 크기 4바이트 + "WEBP" 순서다.
        if (startsWith(content, 'R', 'I', 'F', 'F') && content.length >= 12
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return Optional.of(WEBP);
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
