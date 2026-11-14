package com.ganzithon.wool.upload.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.upload.response.LetterUploadResponse;
import com.ganzithon.wool.upload.response.UploadResponseCode;
import com.ganzithon.wool.upload.service.LetterUploadService;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final LetterUploadService letterUploadService;

    // REPLY-004 손편지 이미지 업로드. 파일이 없으면 required=false로 받아 서비스가 UPLOAD_400_001로 알린다.
    @PostMapping(value = "/letters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LetterUploadResponse>> uploadLetter(
            @LoginUser(Role.SENIOR) User senior,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        return ApiResponse.of(UploadResponseCode.LETTER_UPLOADED, letterUploadService.upload(image)).toResponseEntity();
    }
}
