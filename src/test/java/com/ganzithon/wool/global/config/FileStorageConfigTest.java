package com.ganzithon.wool.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageConfigTest {

    @TempDir
    Path temp;

    @Test
    void 폴더가_아직_없어도_위치는_슬래시로_끝난다() {
        Path missing = temp.resolve("not-created-yet");

        assertThat(Files.exists(missing)).isFalse();
        assertThat(FileStorageConfig.directoryLocation(missing)).endsWith("/not-created-yet/");
    }

    @Test
    void 폴더가_이미_있어도_슬래시가_한_번만_붙는다() throws Exception {
        Path existing = Files.createDirectory(temp.resolve("already-there"));

        assertThat(FileStorageConfig.directoryLocation(existing)).endsWith("/already-there/").doesNotEndWith("//");
    }

    @Test
    void 위치는_file_주소_형태이다() {
        assertThat(FileStorageConfig.directoryLocation(temp.resolve("x"))).startsWith("file:");
    }
}
