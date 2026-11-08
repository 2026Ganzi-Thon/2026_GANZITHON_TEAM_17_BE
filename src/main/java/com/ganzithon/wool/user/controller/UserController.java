package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.request.JoinRequest;
import com.ganzithon.wool.user.response.JoinResponse;
import com.ganzithon.wool.user.response.UserResponseCode;
import com.ganzithon.wool.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // USER-001 가입. 인증 헤더 없이 호출한다.
    @PostMapping
    public ResponseEntity<ApiResponse<JoinResponse>> join(@Valid @RequestBody JoinRequest request) {
        JoinResponse response = JoinResponse.from(userService.join(request.role(), request.ageGroup()));
        return ApiResponse.of(UserResponseCode.JOINED, response).toResponseEntity();
    }
}
