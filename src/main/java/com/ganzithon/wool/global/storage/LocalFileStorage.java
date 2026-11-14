package com.ganzithon.wool.global.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

// 서버 로컬 폴더에 저장한다. 저장한 파일은 FileStorageConfig가 /files/** 로 내려준다.
@Component
public class LocalFileStorage implements FileStorage {

    public static final String PUBLIC_PREFIX = "/files/";

    private static final Pattern SAFE_DIRECTORY = Pattern.compile("[a-z0-9-]+");
    private static final Pattern SAFE_FILENAME = Pattern.compile("[a-z0-9-]+\\.[a-z0-9]+");

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    @Override
    public boolean exists(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(PUBLIC_PREFIX)) {
            return false;
        }
        String[] parts = publicPath.substring(PUBLIC_PREFIX.length()).split("/", -1);
        if (parts.length != 2 || !SAFE_DIRECTORY.matcher(parts[0]).matches() || !SAFE_FILENAME.matcher(parts[1]).matches()) {
            return false;
        }
        Path target = root.resolve(parts[0]).resolve(parts[1]).normalize();
        return target.startsWith(root) && Files.isRegularFile(target);
    }

    @Override
    public String save(String directory, String filename, byte[] content) {
        // 경로를 바꾸는 문자(/, .., 역슬래시 등)가 들어오면 개발 실수이므로 저장하지 않는다.
        if (!SAFE_DIRECTORY.matcher(directory).matches() || !SAFE_FILENAME.matcher(filename).matches()) {
            throw new IllegalArgumentException("저장 경로에 쓸 수 없는 문자가 있습니다.");
        }
        Path target = root.resolve(directory).resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("저장 폴더 밖에는 저장할 수 없습니다.");
        }
        try {
            Files.createDirectories(target.getParent());
            // 같은 이름이 이미 있으면 덮어쓰지 않고 실패한다.
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 저장하지 못했습니다.", e);
        }
        return PUBLIC_PREFIX + directory + "/" + filename;
    }
}
