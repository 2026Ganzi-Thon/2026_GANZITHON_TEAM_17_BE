package com.ganzithon.wool.upload.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.storage.FileStorage;
import com.ganzithon.wool.upload.exception.UploadErrorCode;
import com.ganzithon.wool.upload.response.LetterUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

// 손편지 사진을 있는 그대로 저장한다. (보정·내용 검사 없음) 저장 이름은 원본 파일명과 상관없이 새 UUID로 만든다.
@Service
@RequiredArgsConstructor
public class LetterUploadService {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final FileStorage fileStorage;

    public LetterUploadResponse upload(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(UploadErrorCode.INVALID_IMAGE);
        }
        if (image.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(UploadErrorCode.TOO_LARGE);
        }
        byte[] content = read(image);
        ImageFormat format = ImageFormat.detect(content)
                .orElseThrow(() -> new BusinessException(UploadErrorCode.INVALID_IMAGE));

        // 그림은 그대로 두고, 촬영 위치·기기 정보 같은 부가 정보만 지운다. (주소를 아는 누구나 사진을 열 수 있어서 위치가 샐 수 있다)
        // 사진 구조를 읽지 못하면 위치 정보가 남은 채 저장될 수 있으므로 받지 않는다.
        byte[] stored;
        try {
            stored = ImageMetadataStripper.strip(format, content);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(UploadErrorCode.INVALID_IMAGE);
        }

        String filename = UUID.randomUUID() + "." + format.extension();
        return new LetterUploadResponse(fileStorage.save(LetterImageUrl.DIRECTORY, filename, stored));
    }

    private byte[] read(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드한 파일을 읽지 못했습니다.", e);
        }
    }
}
