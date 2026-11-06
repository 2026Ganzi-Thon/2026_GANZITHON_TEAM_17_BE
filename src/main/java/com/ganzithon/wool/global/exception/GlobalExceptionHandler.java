package com.ganzithon.wool.global.exception;

import com.ganzithon.wool.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

// 프레임워크가 던지는 표준 예외(404, 405, 415, 본문 파싱 오류 등)는 ResponseEntityExceptionHandler가 잡고,
// 응답 형식만 ApiResponse로 바꾼다.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("event=business_exception code={} message={}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, exception.getData()));
    }

    // @RequestBody @Valid 검증 실패
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "reason", String.valueOf(fieldError.getDefaultMessage())))
                .toList();
        return invalidInput(Map.of("errors", errors));
    }

    // @RequestParam, @PathVariable 검증 실패 (Spring 6.1+의 메서드 파라미터 검증)
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return invalidInput(null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "reason", violation.getMessage()))
                .toList();
        return invalidInput(Map.of("errors", errors));
    }

    // enum, 숫자 등 파라미터 타입 변환 실패
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidInput(Map.of("errors", List.of(Map.of(
                "field", exception.getName(),
                "reason", "올바르지 않은 값입니다."))));
    }

    // 본문 JSON 파싱 오류, 필수 파라미터 누락 등 나머지 표준 예외
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        HttpStatus httpStatus = HttpStatus.valueOf(statusCode.value());
        if (httpStatus.is5xxServerError()) {
            log.error("event=framework_exception", exception);
        }
        String code = httpStatus == HttpStatus.BAD_REQUEST
                ? CommonErrorCode.INVALID_INPUT.getCode()
                : "COMMON_" + httpStatus.value();
        String message = httpStatus == HttpStatus.BAD_REQUEST
                ? CommonErrorCode.INVALID_INPUT.getMessage()
                : httpStatus.getReasonPhrase();
        return ResponseEntity
                .status(httpStatus)
                .body(ApiResponse.error(httpStatus, code, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("event=unexpected_exception", exception);
        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<Object> invalidInput(Object data) {
        return ResponseEntity
                .status(CommonErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT, data));
    }
}
