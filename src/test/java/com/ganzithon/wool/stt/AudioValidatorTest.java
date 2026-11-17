package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioValidatorTest {
    @TempDir Path directory;

    @Test
    void 크기와_확장자를_확인한다() {
        var validator = new AudioValidator("ffmpeg");
        for (var file : List.of(
                new MockMultipartFile("audio", "empty.wav", "audio/wav", new byte[0]),
                new MockMultipartFile("audio", "file.txt", "text/plain", new byte[]{1}),
                new MockMultipartFile("audio", "large.wav", "audio/wav", new byte[10 * 1024 * 1024 + 1]))) {
            assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SttErrorCode.INVALID_AUDIO);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "STT_TEST_FFMPEG", matches = ".+")
    void 실제_음성_형식과_삼분_경계를_검증한다() throws Exception {
        String ffmpeg = System.getenv("STT_TEST_FFMPEG");
        var validator = new AudioValidator(ffmpeg);
        for (String extension : List.of("webm", "m4a", "mp3", "wav", "ogg")) {
            Path file = generate(ffmpeg, extension, "1");
            assertThatCode(() -> validator.validate(upload(file))).as(extension).doesNotThrowAnyException();
        }
        Path exact = generate(ffmpeg, "wav", "180");
        Path over = generate(ffmpeg, "wav", "180.1");
        assertThatCode(() -> validator.validate(upload(exact))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(upload(over))).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(SttErrorCode.INVALID_AUDIO);
        byte[] wav = new byte[10 * 1024 * 1024];
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(wav.length - 8).put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1);
        header.putInt(48000).putInt(96000).putShort((short) 2).putShort((short) 16);
        header.put(new byte[]{'d', 'a', 't', 'a'}).putInt(wav.length - 44);
        var exactSize = new MockMultipartFile("audio", "limit.wav", "audio/wav", wav);
        assertThatCode(() -> validator.validate(exactSize)).doesNotThrowAnyException();
    }

    private Path generate(String ffmpeg, String extension, String seconds) throws Exception {
        Path output = directory.resolve("audio-" + seconds + "." + extension);
        Process process = new ProcessBuilder(ffmpeg, "-v", "error", "-f", "lavfi", "-i",
                "anullsrc=r=16000:cl=mono", "-t", seconds, "-y", output.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(extension).isZero();
        return output;
    }

    private MockMultipartFile upload(Path path) throws Exception {
        return new MockMultipartFile("audio", path.getFileName().toString(), "application/octet-stream", Files.readAllBytes(path));
    }
}
