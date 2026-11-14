package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.reply.response.SeniorResponseCode;
import com.ganzithon.wool.reply.response.SeniorWorryDetailResponse;
import com.ganzithon.wool.reply.response.SeniorWorryListResponse;
import com.ganzithon.wool.reply.service.SeniorWorryService;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/senior/worries")
@RequiredArgsConstructor
public class SeniorWorryController {

    private final SeniorWorryService seniorWorryService;

    // REPLY-002 고민 목록 조회 (어르신)
    @GetMapping
    public ResponseEntity<ApiResponse<SeniorWorryListResponse>> list(@LoginUser(Role.SENIOR) User senior) {
        return ApiResponse.of(SeniorResponseCode.WORRY_LIST, seniorWorryService.list(senior)).toResponseEntity();
    }

    // REPLY-009 고민 상세 조회 (어르신)
    @GetMapping("/{worryId}")
    public ResponseEntity<ApiResponse<SeniorWorryDetailResponse>> detail(
            @LoginUser(Role.SENIOR) User senior, @PathVariable Long worryId) {
        return ApiResponse.of(SeniorResponseCode.WORRY_DETAIL, seniorWorryService.detail(senior, worryId))
                .toResponseEntity();
    }
}
