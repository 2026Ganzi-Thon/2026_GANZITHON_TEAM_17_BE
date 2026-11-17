package com.ganzithon.wool.stt;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "STT_REAL_AUDIO", matches = ".+")
class SttLiveIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;

    @Test
    void 한국어_녹음을_API로_전달해_Groq_응답까지_확인한다() throws Exception {
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "음성실제검증"));
        try {
            String boundary = "wool-live-test-boundary";
            var body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; "
                    + "filename=\"korean.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(Files.readAllBytes(Path.of(System.getenv("STT_REAL_AUDIO"))));
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/stt"))
                    .timeout(Duration.ofSeconds(50)).header("X-User-Id", senior.getId())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("HTTP 응답").isEqualTo(200);
            var json = JsonMapper.builder().build().readTree(response.body());
            assertThat(json.path("code").asText()).isEqualTo("STT_200_1");
            String text = json.path("data").path("text").asText();
            assertThat(text).contains("안녕");
        } finally {
            users.deleteById(senior.getId());
        }
    }
}
