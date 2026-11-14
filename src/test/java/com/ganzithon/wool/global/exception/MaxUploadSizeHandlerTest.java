package com.ganzithon.wool.global.exception;

import com.ganzithon.wool.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

// 멀티파트 용량 상한은 컨트롤러에 닿기 전에 서블릿 계층에서 걸리므로, 예외 처리기가 올바른 응답으로 바꾸는지만 따로 확인한다.
class MaxUploadSizeHandlerTest {

    @Test
    void 용량_상한을_넘으면_413과_업로드_코드로_응답한다() {
        ResponseEntity<Object> response = new GlobalExceptionHandler().handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(10L * 1024 * 1024), new HttpHeaders(), HttpStatus.PAYLOAD_TOO_LARGE, null);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(413);
        assertThat(body.code()).isEqualTo("UPLOAD_413_001");
        assertThat(body.message()).isEqualTo("사진 용량이 너무 커요.");
        assertThat(body.data()).isNull();
    }
}
