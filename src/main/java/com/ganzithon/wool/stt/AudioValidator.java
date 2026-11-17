package com.ganzithon.wool.stt;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class AudioValidator {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    // 16kHz, mono, signed 16-bit PCM: 32,000 bytes per second.
    private static final long MAX_PCM_BYTES = 180L * 32000;
    private static final Set<String> EXTENSIONS = Set.of("webm", "m4a", "mp3", "wav", "ogg");
    private final String ffmpeg;

    public AudioValidator(@Value("${groq.ffmpeg:ffmpeg}") String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public void validate(MultipartFile audio) {
        if (audio == null || audio.isEmpty() || audio.getSize() > MAX_SIZE) {
            throw invalid();
        }
        String filename = audio.getOriginalFilename();
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw invalid();
        }
        Path temp = null;
        Process process = null;
        try {
            temp = Files.createTempFile("wool-stt-", "." + extension);
            audio.transferTo(temp);
            byte[] header;
            try (InputStream in = Files.newInputStream(temp)) {
                header = in.readNBytes(12);
            }
            if (!matchesHeader(extension, header)) {
                throw invalid();
            }
            try {
                process = new ProcessBuilder(ffmpeg, "-nostdin", "-v", "error", "-xerror", "-protocol_whitelist", "file,pipe",
                    "-i", temp.toString(), "-map", "0:a:0", "-t", "181", "-ac", "1", "-ar", "16000",
                    "-f", "s16le", "pipe:1").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException e) {
                throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
            Process running = process;
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                var read = executor.submit(() -> {
                    long total = 0;
                    byte[] buffer = new byte[8192];
                    try (InputStream in = running.getInputStream()) {
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            if (total > MAX_PCM_BYTES) {
                                running.destroyForcibly();
                                return total;
                            }
                        }
                    }
                    return total;
                });
                long decoded = read.get(20, TimeUnit.SECONDS);
                if (!running.waitFor(2, TimeUnit.SECONDS) || running.exitValue() != 0
                        || decoded == 0 || decoded > MAX_PCM_BYTES) {
                    throw invalid();
                }
            } finally {
                if (running.isAlive()) {
                    running.destroyForcibly();
                }
                executor.shutdownNow();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw invalid();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 임시 파일 정리 실패가 기존 처리 결과를 가리지 않도록 한다.
                }
            }
        }
    }

    private static boolean matchesHeader(String extension, byte[] b) {
        if (b.length < 12) {
            return false;
        }
        return switch (extension) {
            case "webm" -> (b[0] & 255) == 0x1a && (b[1] & 255) == 0x45 && (b[2] & 255) == 0xdf && (b[3] & 255) == 0xa3;
            case "m4a" -> b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
            case "ogg" -> b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S';
            case "wav" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
            case "mp3" -> (b[0] == 'I' && b[1] == 'D' && b[2] == '3') || ((b[0] & 255) == 255 && (b[1] & 224) == 224);
            default -> false;
        };
    }

    private static BusinessException invalid() {
        return new BusinessException(SttErrorCode.INVALID_AUDIO);
    }
}
