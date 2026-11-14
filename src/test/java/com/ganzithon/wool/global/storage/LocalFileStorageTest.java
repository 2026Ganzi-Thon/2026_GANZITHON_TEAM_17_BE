package com.ganzithon.wool.global.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage storage() {
        return new LocalFileStorage(new StorageProperties(root.toString()));
    }

    @Test
    void 저장하면_공개_경로를_돌려주고_내용을_그대로_쓴다() throws Exception {
        byte[] content = {1, 2, 3, (byte) 0xFF, 0};

        String url = storage().save("letters", "abc-123.jpg", content);

        assertThat(url).isEqualTo("/files/letters/abc-123.jpg");
        assertThat(Files.readAllBytes(root.resolve("letters").resolve("abc-123.jpg"))).isEqualTo(content);
    }

    @Test
    void 폴더가_없으면_만들고_같은_폴더에_계속_저장한다() throws Exception {
        LocalFileStorage storage = storage();
        storage.save("letters", "a.png", new byte[]{1});
        storage.save("letters", "b.png", new byte[]{2});

        try (Stream<Path> files = Files.list(root.resolve("letters"))) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    void 같은_이름이_있으면_덮어쓰지_않고_실패한다() throws Exception {
        LocalFileStorage storage = storage();
        storage.save("letters", "same.png", new byte[]{1});

        assertThatThrownBy(() -> storage.save("letters", "same.png", new byte[]{2}))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(Files.readAllBytes(root.resolve("letters").resolve("same.png"))).isEqualTo(new byte[]{1});
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.png", "..\\evil.png", "a/b.png", "/abs.png", "A.png", "a b.png", "noextension", ".png", "a.", "", "a.png\n", "a..png", "한글.png"})
    void 경로를_바꾸는_문자가_파일_이름에_있으면_저장하지_않는다(String filename) {
        assertThatThrownBy(() -> storage().save("letters", filename, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(root.toFile().list()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../letters", "a/b", "/abs", "Letters", "", "a.b"})
    void 경로를_바꾸는_문자가_폴더_이름에_있으면_저장하지_않는다(String directory) {
        assertThatThrownBy(() -> storage().save(directory, "ok.png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(root.toFile().list()).isEmpty();
    }

    @Test
    void 저장한_파일은_공개_경로로_존재를_확인한다() {
        LocalFileStorage storage = storage();
        String url = storage.save("letters", "abc-123.jpg", new byte[]{1});

        assertThat(storage.exists(url)).isTrue();
        assertThat(storage.exists("/files/letters/other-456.jpg")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "letters/abc-123.jpg", "/letters/abc-123.jpg", "/files/", "/files/letters", "/files/letters/",
            "/files/letters/abc-123.jpg/", "/files/letters/../letters/abc-123.jpg", "/files/letters/sub/abc-123.jpg",
            "/files//abc-123.jpg", "/files/Letters/abc-123.jpg", "/files/letters/ABC-123.jpg", "https://x/files/letters/abc-123.jpg"})
    void 형식이_다르거나_경로를_벗어나는_주소는_없는_것으로_본다(String publicPath) {
        LocalFileStorage storage = storage();
        storage.save("letters", "abc-123.jpg", new byte[]{1});

        assertThat(storage.exists(publicPath)).isFalse();
    }

    @Test
    void null과_폴더_주소는_없는_것으로_본다() {
        LocalFileStorage storage = storage();
        storage.save("letters", "abc-123.jpg", new byte[]{1});

        assertThat(storage.exists(null)).isFalse();
        assertThat(storage.exists("/files/letters/x.d")).isFalse();
    }
}
