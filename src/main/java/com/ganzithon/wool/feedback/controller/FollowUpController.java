package com.ganzithon.wool.feedback.controller;

import com.ganzithon.wool.feedback.request.CreateFollowUpRequest;
import com.ganzithon.wool.feedback.response.CreateFollowUpResponse;
import com.ganzithon.wool.feedback.response.FollowUpResponseCode;
import com.ganzithon.wool.feedback.service.FollowUpService;
import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replies/{replyId}/follow-ups")
@RequiredArgsConstructor
public class FollowUpController {
    private final FollowUpService service;
    @PostMapping
    public ResponseEntity<ApiResponse<CreateFollowUpResponse>> create(@LoginUser(Role.YOUTH) User user,
            @PathVariable Long replyId, @Valid @RequestBody CreateFollowUpRequest request) {
        return ApiResponse.of(FollowUpResponseCode.CREATED, service.create(user.getId(), replyId, request)).toResponseEntity();
    }
}
