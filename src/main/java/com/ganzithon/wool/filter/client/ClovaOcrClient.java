package com.ganzithon.wool.filter.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ganzithon.wool.filter.config.ClovaOcrProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// 네이버 클라우드 CLOVA OCR(General)로 사진의 글자를 읽는다. 사진은 multipart로 보내고(base64보다 작다),
// 응답의 images[0].fields[].inferText를 이어 붙인다. 글을 고치거나 저장하지 않는다.
//
// 동시에 진행하는 호출 수를 제한한다. (서버 메모리가 작아서, 사진이 한꺼번에 몰리면 메모리를 많이 쓰기 때문)
// 한도가 차 있으면 5초만 기다리고 넘으면 실패시킨다. 호출 자체의 연결·읽기 제한은 timeout이다.
// (둘이 겹쳐도 사용자는 최대 약 15초 기다린다.)
@Component
public class ClovaOcrClient implements OcrClient {

    static final Duration ACQUIRE_WAIT = Duration.ofSeconds(5);

    private final ClovaOcrProperties properties;
    private final RestClient restClient;
    private final Semaphore permits;
    private final Duration acquireWait;

    @Autowired
    public ClovaOcrClient(ClovaOcrProperties properties) {
        this(properties, createRestClient(properties), ACQUIRE_WAIT);
    }

    // 테스트에서 MockRestServiceServer를 연결한 RestClient를 넣을 수 있게 열어 둔다.
    ClovaOcrClient(ClovaOcrProperties properties, RestClient restClient) {
        this(properties, restClient, ACQUIRE_WAIT);
    }

    ClovaOcrClient(ClovaOcrProperties properties, RestClient restClient, Duration acquireWait) {
        this.properties = properties;
        this.restClient = restClient;
        this.permits = new Semaphore(Math.max(1, properties.maxConcurrentCalls()));
        this.acquireWait = acquireWait;
    }

    static RestClient createRestClient(ClovaOcrProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isConfigured() {
        return properties.hasCredentials();
    }

    @Override
    public String extractText(byte[] image, String format) {
        acquire();
        try {
            return requestText(image, format);
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        try {
            if (!permits.tryAcquire(acquireWait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("OCR 동시 호출 한도를 넘었습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR 호출이 중단되었습니다.", e);
        }
    }

    private String requestText(byte[] image, String format) {
        // format은 호출하는 쪽이 정한 jpg/png뿐이라 JSON에 그대로 넣어도 안전하다.
        String message = "{\"version\":\"V2\",\"requestId\":\"" + UUID.randomUUID()
                + "\",\"timestamp\":" + System.currentTimeMillis()
                + ",\"lang\":\"ko\",\"images\":[{\"format\":\"" + format + "\",\"name\":\"letter\"}]}";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("message", message);
        body.add("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "letter." + format;
            }
        });

        OcrResponse response = restClient.post()
                .uri(URI.create(properties.url()))
                .header("X-OCR-SECRET", properties.secret())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OcrResponse.class);
        return response == null ? failure() : response.text();
    }

    private static String failure() {
        throw new IllegalStateException("OCR 응답이 비어 있습니다.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OcrResponse(List<Image> images) {

        // SUCCESS면 읽은 글자를, FAILURE(읽을 글자를 못 찾음)면 빈 문자열을, 그 밖(ERROR 등)은 예외로 알린다.
        String text() {
            if (images == null || images.isEmpty() || images.get(0) == null) {
                throw new IllegalStateException("OCR 응답에 결과가 없습니다.");
            }
            Image image = images.get(0);
            String result = image.inferResult();
            if ("FAILURE".equals(result)) {
                return "";
            }
            if (!"SUCCESS".equals(result)) {
                throw new IllegalStateException("OCR 처리에 실패했습니다.");
            }
            StringBuilder text = new StringBuilder();
            if (image.fields() != null) {
                for (Field field : image.fields()) {
                    if (field == null || field.inferText() == null) {
                        continue;
                    }
                    text.append(field.inferText()).append(Boolean.TRUE.equals(field.lineBreak()) ? '\n' : ' ');
                }
            }
            return text.toString().strip();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Image(String inferResult, List<Field> fields) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Field(String inferText, Boolean lineBreak) {
        }
    }
}
