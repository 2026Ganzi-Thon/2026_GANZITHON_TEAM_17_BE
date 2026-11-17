package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class GroqSttClient {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public GroqSttClient(@Value("${groq.api-key:}") String apiKey,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${groq.model:whisper-large-v3-turbo}") String model,
            @Value("${groq.timeout:30s}") Duration timeout, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public String transcribe(MultipartFile audio) {
        if (apiKey == null || apiKey.isBlank()) {
            throw failed();
        }
        try {
            String boundary = "wool-" + UUID.randomUUID();
            String original = audio.getOriginalFilename();
            String extension = original.substring(original.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n" + model + "\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nko\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio." + extension + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
            body.write(audio.getBytes());
            write(body, "\r\n--" + boundary + "--\r\n");
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/audio/transcriptions"))
                    .timeout(timeout).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            HttpResponse<String> response;
            try {
                response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                if (!pending.isDone()) {
                    pending.cancel(true);
                }
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failed();
            }
            var text = mapper.readTree(response.body()).path("text");
            if (!text.isTextual()) {
                throw failed();
            }
            return text.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw failed();
        }
    }

    private static void write(ByteArrayOutputStream stream, String value) throws java.io.IOException {
        stream.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException failed() {
        return new BusinessException(SttErrorCode.TRANSCRIPTION_FAILED);
    }
}
