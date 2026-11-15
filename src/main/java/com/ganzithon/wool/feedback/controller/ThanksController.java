package com.ganzithon.wool.feedback.controller;

import com.ganzithon.wool.feedback.request.CreateThanksRequest;
import com.ganzithon.wool.feedback.response.CreateThanksResponse;
import com.ganzithon.wool.feedback.response.ThanksResponseCode;
import com.ganzithon.wool.feedback.service.ThanksService;
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
@RequestMapping("/api/replies/{replyId}/thanks")
@RequiredArgsConstructor
public class ThanksController {
    private final ThanksService thanksService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateThanksResponse>> create(
            @LoginUser(Role.YOUTH) User user, @PathVariable Long replyId,
            @Valid @RequestBody CreateThanksRequest request) {
        return ApiResponse.of(ThanksResponseCode.CREATED, thanksService.create(user.getId(), replyId, request))
                .toResponseEntity();
    }
}
