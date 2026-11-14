package com.ganzithon.wool.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 파일을 저장하는 폴더. 기본값은 실행 위치의 uploads 폴더이고, FILE_STORAGE_DIR 환경 변수로 바꾼다.
@ConfigurationProperties(prefix = "file.storage")
public record StorageProperties(@DefaultValue("./uploads") String dir) {
}
