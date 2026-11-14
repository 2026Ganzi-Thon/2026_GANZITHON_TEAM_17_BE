package com.ganzithon.wool.global.controller;

import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.global.response.CommonResponseCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 컨테이너 헬스체크용. 로그인 헤더 없이 부른다. DB 등 외부 연결은 확인하지 않고, 앱이 요청을 받을 수 있는지만 알려준다.
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Void>> health() {
        return ApiResponse.of(CommonResponseCode.OK).toResponseEntity();
    }
}
