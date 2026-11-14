package com.ganzithon.wool.upload.controller;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 처음 배포한 서버처럼 저장 폴더가 아직 없는 상태로 앱이 시작해도, 올린 사진이 /files/** 로 열리는지 확인한다.
// (폴더가 없을 때 Path.toUri()는 끝에 /를 붙이지 않아, 정적 리소스 위치가 폴더로 해석되지 않을 수 있다)
@AutoConfigureMockMvc
class UploadFolderCreatedLaterTest extends IntegrationTestSupport {

    // 이 테스트만의 새 경로라 앱이 시작될 때는 존재하지 않는다.
    private static final Path FRESH_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "wool-fresh-" + UUID.randomUUID());

    @DynamicPropertySource
    static void storageDir(DynamicPropertyRegistry registry) {
        registry.add("file.storage.dir", FRESH_ROOT::toString);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;

    @AfterAll
    static void cleanUp() throws Exception {
        FileSystemUtils.deleteRecursively(FRESH_ROOT);
    }

    @Test
    void 저장_폴더가_없는_상태로_시작해도_올린_사진을_열_수_있다() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
        User senior = users.saveAndFlush(User.join(Role.SENIOR, AgeGroup.AGE_60S, "새폴" + UUID.randomUUID().toString().substring(0, 6)));

        String body = mockMvc.perform(multipart("/api/uploads/letters")
                        .file(new MockMultipartFile("image", "a.jpg", "image/jpeg", jpeg))
                        .header("X-User-Id", senior.getId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String imageUrl = body.replaceAll(".*\"imageUrl\":\"([^\"]+)\".*", "$1");

        assertThat(Files.exists(FRESH_ROOT.resolve("letters"))).isTrue();
        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(jpeg));
    }
}
