package com.ganzithon.wool.worry.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.request.ChangeVisibilityRequest;
import com.ganzithon.wool.worry.request.CreateWorryRequest;
import com.ganzithon.wool.worry.request.UpdateWorryRequest;
import com.ganzithon.wool.worry.response.ChangeVisibilityResponse;
import com.ganzithon.wool.worry.response.CreateWorryResponse;
import com.ganzithon.wool.worry.response.MyWorryListResponse;
import com.ganzithon.wool.worry.response.UpdateWorryResponse;
import com.ganzithon.wool.worry.response.WorryResponseCode;
import com.ganzithon.wool.worry.service.MyWorryListService;
import com.ganzithon.wool.worry.service.WorryService;
import com.ganzithon.wool.worry.service.WorryUpdateService;
import com.ganzithon.wool.worry.service.WorryVisibilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worries")
@RequiredArgsConstructor
public class WorryController {
    private final WorryService worryService;
    private final MyWorryListService myWorryListService;
    private final WorryVisibilityService worryVisibilityService;
    private final WorryUpdateService worryUpdateService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyWorryListResponse>> myWorries(@LoginUser(Role.YOUTH) User user) {
        return ApiResponse.of(WorryResponseCode.MY_LIST, myWorryListService.findFor(user.getId())).toResponseEntity();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateWorryResponse>> create(
            @LoginUser(Role.YOUTH) User user, @Valid @RequestBody CreateWorryRequest request) {
        return ApiResponse.of(WorryResponseCode.CREATED, worryService.create(user, request)).toResponseEntity();
    }

    @PatchMapping("/{worryId}")
    public ResponseEntity<ApiResponse<UpdateWorryResponse>> update(
            @LoginUser(Role.YOUTH) User user, @PathVariable Long worryId,
            @Valid @RequestBody UpdateWorryRequest request) {
        return ApiResponse.of(WorryResponseCode.UPDATED,
                worryUpdateService.update(user.getId(), worryId, request)).toResponseEntity();
    }

    @PatchMapping("/{worryId}/visibility")
    public ResponseEntity<ApiResponse<ChangeVisibilityResponse>> changeVisibility(
            @LoginUser(Role.YOUTH) User user, @PathVariable Long worryId,
            @Valid @RequestBody ChangeVisibilityRequest request) {
        return ApiResponse.of(WorryResponseCode.VISIBILITY_CHANGED,
                worryVisibilityService.change(user.getId(), worryId, request.visibility())).toResponseEntity();
    }
}
