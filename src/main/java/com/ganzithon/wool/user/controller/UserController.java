package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.request.JoinRequest;
import com.ganzithon.wool.user.response.JoinResponse;
import com.ganzithon.wool.user.response.MyInfoResponse;
import com.ganzithon.wool.user.response.NicknameSuggestionResponse;
import com.ganzithon.wool.user.response.UserResponseCode;
import com.ganzithon.wool.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> myInfo(@LoginUser User user) {
        return ApiResponse.of(UserResponseCode.MY_INFO, MyInfoResponse.from(user)).toResponseEntity();
    }

    // 가입 화면에서 처음 보여줄 닉네임을 받는다. 인증 헤더 없이 호출한다. (예약이 아니므로 가입 때 겹치면 409)
    @GetMapping("/nickname-suggestion")
    public ResponseEntity<ApiResponse<NicknameSuggestionResponse>> suggestNickname() {
        NicknameSuggestionResponse response = new NicknameSuggestionResponse(userService.suggestNickname());
        return ApiResponse.of(UserResponseCode.NICKNAME_SUGGESTED, response).toResponseEntity();
    }

    // USER-001 가입. 인증 헤더 없이 호출한다.
    @PostMapping
    public ResponseEntity<ApiResponse<JoinResponse>> join(@Valid @RequestBody JoinRequest request) {
        JoinResponse response = JoinResponse.from(userService.join(request.role(), request.ageGroup()));
        return ApiResponse.of(UserResponseCode.JOINED, response).toResponseEntity();
    }
}
