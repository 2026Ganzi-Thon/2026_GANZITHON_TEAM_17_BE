package com.ganzithon.wool.upload.controller;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.upload.service.LetterUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc는 서블릿 계층의 멀티파트 용량 상한(application.yml)을 거치지 않으므로, 실제 서버를 띄워 같은 설정이 동작하는지 확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadSizeLimitTest {

    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Value("${file.storage.dir}") private String storageDir;

    @AfterEach
    void cleanUp() throws Exception {
        FileSystemUtils.deleteRecursively(Path.of(storageDir).toAbsolutePath().resolve("letters"));
    }

    private HttpResponse<String> upload(String userId, int imageSize) throws Exception {
        byte[] image = new byte[imageSize];
        image[0] = (byte) 0xFF;
        image[1] = (byte) 0xD8;
        image[2] = (byte) 0xFF;
        String boundary = "----wool" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"a.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(image);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/uploads/letters"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-User-Id", userId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 서버가_받는_용량_상한을_넘으면_413과_업로드_코드로_응답한다() throws Exception {
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "한도" + UUID.randomUUID().toString().substring(0, 6)));

        HttpResponse<String> response = upload(senior.getId(), (int) LetterUploadService.MAX_SIZE_BYTES + 1024);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"UPLOAD_413_001\"").contains("사진 용량이 너무 커요.");
        assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().resolve("letters"))).isFalse();
    }

    @Test
    void 상한_안쪽의_사진은_실제_서버에서도_올라간다() throws Exception {
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "한도" + UUID.randomUUID().toString().substring(0, 6)));

        HttpResponse<String> response = upload(senior.getId(), 1024 * 1024);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"code\":\"UPLOAD_201_1\"");
    }
}
