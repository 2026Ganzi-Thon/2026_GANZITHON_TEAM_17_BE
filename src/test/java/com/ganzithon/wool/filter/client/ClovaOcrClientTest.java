package com.ganzithon.wool.filter.client;

import com.ganzithon.wool.filter.config.ClovaOcrProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// CLOVA OCR(General) 호출. 요청 모양(multipart의 message·file, 시크릿 헤더)과 응답 해석, 동시 호출 제한을 확인한다.
class ClovaOcrClientTest {

    private static final String URL = "https://clova.example/custom/v1/1/key/general";
    private static final byte[] IMAGE = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private MockRestServiceServer server;
    private ClovaOcrClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ClovaOcrClient(new ClovaOcrProperties(URL, "secret-key", Duration.ofSeconds(1), 2), builder.build());
    }

    private static String response(String inferResult, String fields) {
        return "{\"version\":\"V2\",\"images\":[{\"inferResult\":\"" + inferResult + "\",\"fields\":" + fields + "}]}";
    }

    @Test
    void 호출_주소와_시크릿이_모두_있어야_설정된_것으로_본다() {
        assertThat(new ClovaOcrProperties(URL, "s", Duration.ofSeconds(1), 2).hasCredentials()).isTrue();
        assertThat(new ClovaOcrProperties("", "s", Duration.ofSeconds(1), 2).hasCredentials()).isFalse();
        assertThat(new ClovaOcrProperties(URL, " ", Duration.ofSeconds(1), 2).hasCredentials()).isFalse();
        assertThat(new ClovaOcrProperties(null, null, Duration.ofSeconds(1), 2).hasCredentials()).isFalse();
    }

    @Test
    void multipart로_message와_사진을_시크릿_헤더와_함께_보내고_읽은_글자를_줄바꿈까지_이어_붙인다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-OCR-SECRET", "secret-key"))
                .andExpect(header("Content-Type", startsWith("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"message\"")))
                .andExpect(content().string(containsString("\"version\":\"V2\"")))
                .andExpect(content().string(containsString("\"lang\":\"ko\"")))
                .andExpect(content().string(containsString("\"format\":\"png\"")))
                .andExpect(content().string(containsString("name=\"file\"; filename=\"letter.png\"")))
                .andRespond(withSuccess(response("SUCCESS",
                        "[{\"inferText\":\"안녕\",\"lineBreak\":false},{\"inferText\":\"하세요\",\"lineBreak\":true},"
                                + "{\"inferText\":\"010-1234\",\"lineBreak\":false},{\"inferText\":\"5678\"}]"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.extractText(IMAGE, "png")).isEqualTo("안녕 하세요\n010-1234 5678");
        server.verify();
    }

    @Test
    void 읽을_글자가_없어_FAILURE이면_빈_문자열이다() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("FAILURE", "[]"), MediaType.APPLICATION_JSON));

        assertThat(client.extractText(IMAGE, "jpg")).isEmpty();
    }

    @Test
    void 성공이어도_글자가_없으면_빈_문자열이다() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("SUCCESS", "[]"), MediaType.APPLICATION_JSON));

        assertThat(client.extractText(IMAGE, "jpg")).isEmpty();
    }

    @Test
    void ERROR나_알_수_없는_결과는_예외로_알린다() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("ERROR", "[]"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extractText(IMAGE, "jpg")).isInstanceOf(IllegalStateException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"images\":[{\"fields\":[]}]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extractText(IMAGE, "jpg")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 결과가_비어_있거나_본문이_없으면_예외로_알린다() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"images\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extractText(IMAGE, "jpg")).isInstanceOf(IllegalStateException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.extractText(IMAGE, "jpg")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키가_틀려_401이면_예외로_알린다() {
        server.expect(requestTo(URL)).andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> client.extractText(IMAGE, "jpg")).isInstanceOf(RuntimeException.class);
    }

    // ---------- 동시 호출 제한 (실제 HTTP 서버로 응답을 붙들어 둔다) ----------

    private HttpServer http;
    private CountDownLatch release;

    @AfterEach
    void stopServer() {
        if (release != null) {
            release.countDown();
        }
        if (http != null) {
            http.stop(0);
        }
    }

    private String startSlowServer() throws IOException {
        release = new CountDownLatch(1);
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/general", exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = response("SUCCESS", "[{\"inferText\":\"안녕\",\"lineBreak\":false}]").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        http.start();
        return "http://127.0.0.1:" + http.getAddress().getPort() + "/general";
    }

    @Test
    void 동시_호출_한도가_차면_기다리다_실패하고_끝난_호출의_자리는_돌려준다() throws Exception {
        String url = startSlowServer();
        // 호출 제한(읽기)은 길게, 자리를 기다리는 시간만 짧게 해서 두 번째 호출이 먼저 포기하게 한다.
        ClovaOcrProperties properties = new ClovaOcrProperties(url, "s", Duration.ofSeconds(5), 1);
        ClovaOcrClient limited = new ClovaOcrClient(properties, ClovaOcrClient.createRestClient(properties), Duration.ofMillis(300));

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> limited.extractText(IMAGE, "png"));
        Thread.sleep(150); // 첫 호출이 자리를 잡고 응답을 기다리는 중이다.

        assertThatThrownBy(() -> limited.extractText(IMAGE, "png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동시 호출 한도");

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("안녕");
        // 자리가 돌아와서 다음 호출은 다시 된다.
        assertThat(limited.extractText(IMAGE, "png")).isEqualTo("안녕");
    }
}
