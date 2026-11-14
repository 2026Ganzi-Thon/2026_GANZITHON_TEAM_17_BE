package com.ganzithon.wool.certificate.controller;

import com.ganzithon.wool.certificate.response.CertificateDetailResponse;
import com.ganzithon.wool.certificate.response.CertificateListResponse;
import com.ganzithon.wool.certificate.response.CertificateResponseCode;
import com.ganzithon.wool.certificate.service.CertificateQueryService;
import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/senior/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateQueryService certificateQueryService;

    // WOOL-003 감사장 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<CertificateListResponse>> list(@LoginUser(Role.SENIOR) User senior) {
        return ApiResponse.of(CertificateResponseCode.LIST, certificateQueryService.list(senior)).toResponseEntity();
    }

    // WOOL-004 감사장 상세 조회
    @GetMapping("/{certificateId}")
    public ResponseEntity<ApiResponse<CertificateDetailResponse>> detail(
            @LoginUser(Role.SENIOR) User senior, @PathVariable Long certificateId) {
        return ApiResponse.of(CertificateResponseCode.DETAIL, certificateQueryService.detail(senior, certificateId))
                .toResponseEntity();
    }
}
