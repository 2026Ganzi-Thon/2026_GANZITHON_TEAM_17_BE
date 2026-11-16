package com.ganzithon.wool.wool.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.wool.response.ReceivedHeartListResponse;
import com.ganzithon.wool.wool.response.WoolResponseCode;
import com.ganzithon.wool.wool.service.ReceivedHeartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/senior")
@RequiredArgsConstructor
public class ReceivedHeartController {

    private final ReceivedHeartService receivedHeartService;

    // WOOL-002 받은 마음 목록 조회 (조회하면 목록의 마음이 읽음 처리된다)
    @GetMapping("/received")
    public ResponseEntity<ApiResponse<ReceivedHeartListResponse>> received(@LoginUser(Role.SENIOR) User senior) {
        return ApiResponse.of(WoolResponseCode.RECEIVED_HEARTS, receivedHeartService.list(senior)).toResponseEntity();
    }
}
