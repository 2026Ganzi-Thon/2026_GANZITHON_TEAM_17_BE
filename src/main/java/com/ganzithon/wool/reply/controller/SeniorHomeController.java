package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.reply.response.SeniorHomeResponse;
import com.ganzithon.wool.reply.response.SeniorResponseCode;
import com.ganzithon.wool.reply.service.SeniorHomeService;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/senior/home")
@RequiredArgsConstructor
public class SeniorHomeController {

    private final SeniorHomeService seniorHomeService;

    // REPLY-001 어르신 홈 요약 조회
    @GetMapping
    public ResponseEntity<ApiResponse<SeniorHomeResponse>> home(@LoginUser(Role.SENIOR) User senior) {
        return ApiResponse.of(SeniorResponseCode.HOME, seniorHomeService.get(senior)).toResponseEntity();
    }
}
