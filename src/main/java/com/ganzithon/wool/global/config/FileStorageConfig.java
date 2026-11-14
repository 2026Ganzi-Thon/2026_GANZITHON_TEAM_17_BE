package com.ganzithon.wool.global.config;

import com.ganzithon.wool.global.storage.LocalFileStorage;
import com.ganzithon.wool.global.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

// 저장한 파일을 /files/** 로 내려준다. 파일 이름은 추측할 수 없는 무작위 UUID라 주소를 아는 사람만 열 수 있다.
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class FileStorageConfig implements WebMvcConfigurer {

    private final Path root;

    public FileStorageConfig(StorageProperties properties) {
        this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 이름이 바뀌지 않는 파일이라 하루 동안 캐시해도 된다.
        registry.addResourceHandler(LocalFileStorage.PUBLIC_PREFIX + "**")
                .addResourceLocations(directoryLocation(root))
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS));
    }

    // 정적 리소스 위치는 폴더로 해석되려면 슬래시로 끝나야 한다. Path.toUri()는 폴더가 이미 있을 때만 슬래시를 붙이므로,
    // 처음 시작해서 저장 폴더가 아직 없어도 같은 결과가 되게 직접 붙인다.
    static String directoryLocation(Path directory) {
        String uri = directory.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
