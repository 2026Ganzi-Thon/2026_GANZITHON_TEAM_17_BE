package com.ganzithon.wool.user.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.request.JoinRequest;
import com.ganzithon.wool.user.request.UpdateNicknameRequest;
import com.ganzithon.wool.user.response.JoinResponse;
import com.ganzithon.wool.user.response.MyInfoResponse;
import com.ganzithon.wool.user.response.UpdateNicknameResponse;
import com.ganzithon.wool.user.response.UserResponseCode;
import com.ganzithon.wool.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    // USER-002 닉네임 수정. 청년·어르신 모두 쓸 수 있다.
    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<UpdateNicknameResponse>> updateNickname(
            @LoginUser User user, @Valid @RequestBody UpdateNicknameRequest request) {
        UpdateNicknameResponse response = new UpdateNicknameResponse(userService.updateNickname(user, request.nickname()));
        return ApiResponse.of(UserResponseCode.NICKNAME_UPDATED, response).toResponseEntity();
    }

    // USER-001 가입. 인증 헤더 없이 호출한다.
    @PostMapping
    public ResponseEntity<ApiResponse<JoinResponse>> join(@Valid @RequestBody JoinRequest request) {
        JoinResponse response = JoinResponse.from(userService.join(request.role(), request.ageGroup(), request.nickname()));
        return ApiResponse.of(UserResponseCode.JOINED, response).toResponseEntity();
    }
}
