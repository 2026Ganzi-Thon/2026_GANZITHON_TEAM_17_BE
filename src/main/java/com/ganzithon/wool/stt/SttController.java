package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class SttController {
    private final SttService service;

    @PostMapping(value = "/api/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SttResponse>> transcribe(@LoginUser(Role.SENIOR) User senior,
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        return ApiResponse.of(SttResponseCode.TRANSCRIBED, service.transcribe(audio)).toResponseEntity();
    }

    public record SttResponse(String text) { }
}
