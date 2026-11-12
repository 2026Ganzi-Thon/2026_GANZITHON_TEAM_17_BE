package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.response.NicknameSuggestionResponse;
import com.ganzithon.wool.user.response.UserResponseCode;
import com.ganzithon.wool.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 닉네임 자체에 대한 API. 특정 사용자에 대한 것이 아니라서(가입 전에도 부른다) /api/users 아래가 아니라 /api/nicknames에 둔다.
@RestController
@RequestMapping("/api/nicknames")
@RequiredArgsConstructor
public class NicknameController {

    private final UserService userService;

    // 가입 화면에서 처음 보여줄 닉네임을 받는다. 인증 헤더 없이 호출한다. (예약이 아니므로 가입 때 겹치면 409)
    @GetMapping("/suggestion")
    public ResponseEntity<ApiResponse<NicknameSuggestionResponse>> suggestNickname() {
        NicknameSuggestionResponse response = new NicknameSuggestionResponse(userService.suggestNickname());
        return ApiResponse.of(UserResponseCode.NICKNAME_SUGGESTED, response).toResponseEntity();
    }
}
