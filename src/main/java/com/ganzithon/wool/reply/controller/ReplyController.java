package com.ganzithon.wool.reply.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.reply.response.CreateReplyResponse;
import com.ganzithon.wool.reply.response.ReplyResponseCode;
import com.ganzithon.wool.reply.service.ReplyCreateService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyCreateService replyCreateService;

    // REPLY-005 답장 작성
    @PostMapping("/worries/{worryId}/replies")
    public ResponseEntity<ApiResponse<CreateReplyResponse>> create(
            @LoginUser(Role.SENIOR) User senior, @PathVariable Long worryId,
            @Valid @RequestBody CreateReplyRequest request) {
        return ApiResponse.of(ReplyResponseCode.CREATED, replyCreateService.create(senior, worryId, request))
                .toResponseEntity();
    }
}
