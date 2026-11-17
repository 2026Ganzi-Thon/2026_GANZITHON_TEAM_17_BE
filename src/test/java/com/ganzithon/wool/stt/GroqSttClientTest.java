package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqSttClientTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "STT_REAL_AUDIO", matches = ".+")
    void 실제_Groq에_테스트_음성을_전송한다() throws Exception {
        Path path = Path.of(System.getenv("STT_REAL_AUDIO"));
        var audio = new MockMultipartFile("audio", path.getFileName().toString(), "audio/wav", Files.readAllBytes(path));
        var client = new GroqSttClient(System.getenv("GROQ_API_KEY"), "https://api.groq.com/openai/v1",
                "whisper-large-v3-turbo", Duration.ofSeconds(30), JsonMapper.builder().build());
        assertThat(client.transcribe(audio)).isNotBlank();
    }

    @Test
    void 모델과_한국어_파일을_전송하고_결과를_반환한다() throws Exception {
        var request = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio/transcriptions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"text\":\"안녕하세요\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = new GroqSttClient("key", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "whisper-large-v3-turbo", Duration.ofSeconds(3), JsonMapper.builder().build());
            var audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1, 2});
            assertThat(client.transcribe(audio)).isEqualTo("안녕하세요");
            assertThat(authorization.get()).isEqualTo("Bearer key");
            assertThat(request.get()).contains("name=\"model\"", "whisper-large-v3-turbo", "name=\"language\"",
                    "\r\n\r\nko\r\n", "filename=\"audio.wav\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 키가_없거나_외부_연결이_실패하면_인식_실패를_반환한다() {
        var audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1});
        for (String key : new String[]{"", "key"}) {
            var client = new GroqSttClient(key, "http://127.0.0.1:1", "model", Duration.ofMillis(300), JsonMapper.builder().build());
            assertThatThrownBy(() -> client.transcribe(audio)).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SttErrorCode.TRANSCRIPTION_FAILED);
        }
    }

    @Test
    void 외부_오류와_응답_시간_초과는_인식_실패를_반환한다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio/transcriptions", exchange -> {
            if (exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer slow")) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            var audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1});
            for (String key : new String[]{"error", "slow"}) {
                var client = new GroqSttClient(key, "http://127.0.0.1:" + server.getAddress().getPort(),
                        "model", Duration.ofMillis(250), JsonMapper.builder().build());
                assertThatThrownBy(() -> client.transcribe(audio)).isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SttErrorCode.TRANSCRIPTION_FAILED);
            }
        } finally {
            server.stop(0);
        }
    }
}
