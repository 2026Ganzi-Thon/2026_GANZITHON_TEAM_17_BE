package com.ganzithon.wool.wool.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.wool.response.WoolRecordResponse;
import com.ganzithon.wool.wool.response.WoolResponseCode;
import com.ganzithon.wool.wool.service.WoolRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/senior")
@RequiredArgsConstructor
public class WoolController {

    private final WoolRecordService woolRecordService;

    // WOOL-001 털 기록 조회
    @GetMapping("/wool")
    public ResponseEntity<ApiResponse<WoolRecordResponse>> record(@LoginUser(Role.SENIOR) User senior) {
        return ApiResponse.of(WoolResponseCode.RECORD, woolRecordService.get(senior)).toResponseEntity();
    }
}
