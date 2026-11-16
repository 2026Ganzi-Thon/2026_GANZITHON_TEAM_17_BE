package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.reply.response.SeniorReplyListResponse;
import com.ganzithon.wool.reply.response.SeniorResponseCode;
import com.ganzithon.wool.reply.service.SeniorReplyListService;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/senior/replies")
@RequiredArgsConstructor
public class SeniorReplyController {

    private final SeniorReplyListService seniorReplyListService;

    // REPLY-006 내가 보낸 답장 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<SeniorReplyListResponse>> list(@LoginUser(Role.SENIOR) User senior) {
        return ApiResponse.of(SeniorResponseCode.REPLY_LIST, seniorReplyListService.list(senior)).toResponseEntity();
    }
}
