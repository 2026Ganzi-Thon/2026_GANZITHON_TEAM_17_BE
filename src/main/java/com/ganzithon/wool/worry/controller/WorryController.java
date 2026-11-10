package com.ganzithon.wool.worry.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.request.CreateWorryRequest;
import com.ganzithon.wool.worry.response.CreateWorryResponse;
import com.ganzithon.wool.worry.response.WorryResponseCode;
import com.ganzithon.wool.worry.service.WorryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worries")
@RequiredArgsConstructor
public class WorryController {
    private final WorryService worryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateWorryResponse>> create(
            @LoginUser(Role.YOUTH) User user, @Valid @RequestBody CreateWorryRequest request) {
        return ApiResponse.of(WorryResponseCode.CREATED, worryService.create(user, request)).toResponseEntity();
    }
}
