package com.ganzithon.wool.global.response;

import com.ganzithon.wool.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record ApiResponse<T>(
        int status,
        String code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> of(ResponseCode responseCode, T data) {
        return new ApiResponse<>(
                responseCode.getHttpStatus().value(),
                responseCode.getCode(),
                responseCode.getMessage(),
                data
        );
    }

    public static ApiResponse<Void> of(ResponseCode responseCode) {
        return of(responseCode, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                data
        );
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }

    public static ApiResponse<Void> error(HttpStatus httpStatus, String code, String message) {
        return new ApiResponse<>(httpStatus.value(), code, message, null);
    }

    // 컨트롤러에서: return ApiResponse.of(UserResponseCode.JOINED, data).toResponseEntity();
    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.status(status).body(this);
    }
}
