package com.ganzithon.wool.upload.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.upload.service.LetterUploadService;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.repository.WorryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.util.FileSystemUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UploadControllerTest extends IntegrationTestSupport {

    private static final String URL = "/api/uploads/letters";
    private static final Pattern IMAGE_URL = Pattern.compile("/files/letters/[0-9a-f-]{36}\\.(jpg|png)");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private WorryRepository worries;
    @Value("${file.storage.dir}") private String storageDir;

    private User senior;
    private User youth;

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    @BeforeEach
    void setUp() {
        senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, name("업선")));
        youth = users.saveAndFlush(User.join(Role.YOUTH, AgeGroup.AGE_20S, name("업청")));
    }

    @AfterEach
    void cleanUp() throws Exception {
        FileSystemUtils.deleteRecursively(Path.of(storageDir).toAbsolutePath().resolve("letters"));
    }

    // 사진 구조까지 읽어서 위치 정보를 지우므로 실제로 그려지는 사진을 쓴다.
    private static byte[] jpeg(int size) {
        return com.ganzithon.wool.upload.service.TestImages.jpegOfSize(size);
    }

    private static byte[] png() {
        return com.ganzithon.wool.upload.service.TestImages.png();
    }

    private static byte[] webp() {
        return new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    }

    private ResultActions upload(User sender, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(URL);
        if (file != null) {
            request.file(file);
        }
        if (sender != null) {
            request.header("X-User-Id", sender.getId());
        }
        return mockMvc.perform(request);
    }

    private static MockMultipartFile image(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("image", filename, contentType, content);
    }

    private String imageUrlOf(ResultActions result) throws Exception {
        MvcResult mvcResult = result.andReturn();
        String body = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.replaceAll(".*\"imageUrl\":\"([^\"]+)\".*", "$1");
    }

    private Path fileOf(String imageUrl) {
        return Path.of(storageDir).toAbsolutePath().resolve(imageUrl.substring("/files/".length()));
    }

    // ---------- 성공 ----------

    @Test
    void 사진을_올리면_201과_상대_경로를_래퍼로_내려주고_부가_정보가_없는_사진은_그대로_저장한다() throws Exception {
        byte[] bytes = jpeg(0);
        ResultActions result = upload(senior, image("letter.jpg", "image/jpeg", bytes))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("UPLOAD_201_1"))
                .andExpect(jsonPath("$.message").value("손편지 사진을 올렸습니다."))
                .andExpect(jsonPath("$.data.imageUrl").isString())
                .andExpect(jsonPath("$.data.length()").value(1));

        String imageUrl = imageUrlOf(result);
        assertThat(imageUrl).matches(IMAGE_URL);
        assertThat(imageUrl).endsWith(".jpg");
        assertThat(Files.readAllBytes(fileOf(imageUrl))).isEqualTo(bytes);
    }

    @Test
    void 확장자는_파일_이름이_아니라_실제_형식으로_정한다() throws Exception {
        assertThat(imageUrlOf(upload(senior, image("a.jpg", "image/jpeg", png())).andExpect(status().isCreated()))).endsWith(".png");
        // 이름이 .webp여도 내용이 jpg면 jpg로 저장한다.
        assertThat(imageUrlOf(upload(senior, image("a.webp", "image/webp", jpeg(100))).andExpect(status().isCreated()))).endsWith(".jpg");
    }

    @Test
    void Content_Type을_속여도_실제_형식이_맞으면_저장한다() throws Exception {
        // 브라우저·앱마다 Content-Type이 달라서 믿지 않는다. 판단은 파일 내용으로만 한다.
        upload(senior, image("letter", "application/octet-stream", jpeg(100))).andExpect(status().isCreated());
    }

    @Test
    void 원본_파일_이름은_저장_경로에_쓰지_않는다() throws Exception {
        String imageUrl = imageUrlOf(upload(senior, image("../../../evil name.jpg", "image/jpeg", jpeg(100)))
                .andExpect(status().isCreated()));

        assertThat(imageUrl).matches(IMAGE_URL);
        assertThat(Files.exists(fileOf(imageUrl))).isTrue();
    }

    @Test
    void 같은_사진을_두_번_올려도_주소는_매번_다르다() throws Exception {
        byte[] bytes = jpeg(100);
        String first = imageUrlOf(upload(senior, image("a.jpg", "image/jpeg", bytes)).andExpect(status().isCreated()));
        String second = imageUrlOf(upload(senior, image("a.jpg", "image/jpeg", bytes)).andExpect(status().isCreated()));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 열_메가바이트까지는_올릴_수_있다() throws Exception {
        upload(senior, image("big.jpg", "image/jpeg", jpeg((int) LetterUploadService.MAX_SIZE_BYTES)))
                .andExpect(status().isCreated());
    }

    // ---------- 올린 사진 내려주기 ----------

    @Test
    void 올린_사진은_files_주소로_원본_그대로_내려준다() throws Exception {
        byte[] bytes = png();
        String imageUrl = imageUrlOf(upload(senior, image("a.png", "image/png", bytes)).andExpect(status().isCreated()));

        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void nosniff는_파일_응답에만_붙는다() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(header().doesNotExist("X-Content-Type-Options"));
        mockMvc.perform(get("/files/letters/00000000-0000-0000-0000-000000000000.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void 없는_사진과_폴더_밖_경로는_404이다() throws Exception {
        mockMvc.perform(get("/files/letters/00000000-0000-0000-0000-000000000000.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/files/letters/../../application.yml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/files/letters/%2e%2e/%2e%2e/application.yml")).andExpect(status().isNotFound());
    }

    // ---------- 인증·권한 ----------

    @Test
    void 헤더가_없으면_401이다() throws Exception {
        upload(null, image("a.jpg", "image/jpeg", jpeg(100)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_401_001"));
    }

    @Test
    void 청년은_올릴_수_없고_저장하지_않는다() throws Exception {
        upload(youth, image("a.jpg", "image/jpeg", jpeg(100)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_403_001"));

        assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().resolve("letters"))).isFalse();
    }

    // ---------- 잘못된 파일 400 ----------

    @Test
    void 파일을_보내지_않으면_400이다() throws Exception {
        upload(senior, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("UPLOAD_400_001"))
                .andExpect(jsonPath("$.message").value("사진을 확인해주세요."));
    }

    @Test
    void 이름이_image가_아닌_파일은_받지_않는다() throws Exception {
        upload(senior, new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg(100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
    }

    // 휴대폰 사진에는 촬영 위치와 기기 정보가 들어 있고, 올린 사진은 주소를 아는 누구나 열 수 있다. 저장할 때 지우고 그림은 그대로 둔다.
    @Test
    void 휴대폰_사진의_위치_정보는_저장할_때_지우고_그림은_그대로_둔다() throws Exception {
        byte[] original = com.ganzithon.wool.upload.service.TestImages.jpeg(false);
        byte[] photo = com.ganzithon.wool.upload.service.TestImages.insertAfterSoi(original,
                com.ganzithon.wool.upload.service.TestImages.segment(0xE1, com.ganzithon.wool.upload.service.TestImages.exifWithGps(6)));

        String imageUrl = imageUrlOf(upload(senior, image("camera.jpg", "image/jpeg", photo)).andExpect(status().isCreated()));

        byte[] stored = Files.readAllBytes(fileOf(imageUrl));
        assertThat(com.ganzithon.wool.upload.service.TestImages.contains(stored, "GPSLatitude")).isFalse();
        assertThat(com.ganzithon.wool.upload.service.TestImages.contains(stored, "SM-G991N")).isFalse();
        assertThat(com.ganzithon.wool.upload.service.TestImages.samePixels(stored, original)).isTrue();
        // 공개 주소로 내려받는 사진에도 위치 정보가 없다.
        byte[] served = mockMvc.perform(get(imageUrl)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(com.ganzithon.wool.upload.service.TestImages.contains(served, "GPSLatitude")).isFalse();
    }

    @Test
    void PNG의_위치_정보도_저장할_때_지운다() throws Exception {
        byte[] original = com.ganzithon.wool.upload.service.TestImages.png();
        byte[] photo = com.ganzithon.wool.upload.service.TestImages.insertBeforeIdat(original,
                com.ganzithon.wool.upload.service.TestImages.pngChunk("tEXt",
                        com.ganzithon.wool.upload.service.TestImages.ascii("Location\0" + com.ganzithon.wool.upload.service.TestImages.GPS_TEXT)));

        String imageUrl = imageUrlOf(upload(senior, image("shot.png", "image/png", photo)).andExpect(status().isCreated()));

        byte[] stored = Files.readAllBytes(fileOf(imageUrl));
        assertThat(com.ganzithon.wool.upload.service.TestImages.contains(stored, "GPSLatitude")).isFalse();
        assertThat(com.ganzithon.wool.upload.service.TestImages.samePixels(stored, original)).isTrue();
    }

    // 구조를 읽지 못하는 사진은 위치 정보가 남은 채 저장될 수 있어서 받지 않는다. (일부러 깨뜨려 위치 정보 제거를 피하는 것을 막는다)
    @Test
    void 구조를_읽을_수_없는_사진은_위치_정보가_있어도_받지_않고_저장하지_않는다() throws Exception {
        byte[] jpeg = com.ganzithon.wool.upload.service.TestImages.insertAfterSoi(
                com.ganzithon.wool.upload.service.TestImages.jpeg(false),
                com.ganzithon.wool.upload.service.TestImages.segment(0xE1, com.ganzithon.wool.upload.service.TestImages.exifWithGps(0)));
        byte[] png = com.ganzithon.wool.upload.service.TestImages.insertBeforeIdat(png(),
                com.ganzithon.wool.upload.service.TestImages.pngChunk("tEXt", com.ganzithon.wool.upload.service.TestImages.ascii("a\0" + com.ganzithon.wool.upload.service.TestImages.GPS_TEXT)));
        byte[] fakeJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

        upload(senior, image("a.jpg", "image/jpeg", Arrays.copyOf(jpeg, jpeg.length - 40)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
        upload(senior, image("a.png", "image/png", Arrays.copyOf(png, png.length - 20)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
        upload(senior, image("a.jpg", "image/jpeg", fakeJpeg))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
        assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().resolve("letters"))).isFalse();
    }

    // 손편지 내용 검사(클로바 OCR)가 webp를 읽지 못해서 받지 않는다. 받으면 검사를 피해 갈 수 있다.
    @Test
    void webp는_받지_않는다() throws Exception {
        upload(senior, image("a.webp", "image/webp", webp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
    }

    @Test
    void 빈_파일은_400이다() throws Exception {
        upload(senior, image("a.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
    }

    @Test
    void 이름과_Content_Type이_이미지여도_내용이_이미지가_아니면_400이다() throws Exception {
        for (byte[] fake : new byte[][]{
                "그냥 글자입니다".getBytes(StandardCharsets.UTF_8),
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                "GIF89a............".getBytes(StandardCharsets.US_ASCII),
                "%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII),
                Arrays.copyOf(png(), 5)}) {
            upload(senior, image("a.jpg", "image/jpeg", fake))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UPLOAD_400_001"));
        }
        assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().resolve("letters"))).isFalse();
    }

    @Test
    void 멀티파트가_아닌_요청은_415이다() throws Exception {
        mockMvc.perform(post(URL).header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"image\":\"abc\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------- 용량 413 ----------

    @Test
    void 열_메가바이트를_넘으면_413이고_저장하지_않는다() throws Exception {
        upload(senior, image("big.jpg", "image/jpeg", jpeg((int) LetterUploadService.MAX_SIZE_BYTES + 1)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.code").value("UPLOAD_413_001"))
                .andExpect(jsonPath("$.message").value("사진 용량이 너무 커요."));

        assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().resolve("letters"))).isFalse();
    }

    // ---------- 답장 작성과 이어서 쓰기 ----------

    @Test
    void 올린_주소로_손편지_답장을_보낼_수_있다() throws Exception {
        Worry worry = worries.saveAndFlush(Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC));
        String imageUrl = imageUrlOf(upload(senior, image("a.jpg", "image/jpeg", jpeg(100))).andExpect(status().isCreated()));

        mockMvc.perform(post("/api/worries/{worryId}/replies", worry.getId()).header("X-User-Id", senior.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LETTER\",\"imageUrl\":\"" + imageUrl + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("REPLY_201_1"));
    }
}
