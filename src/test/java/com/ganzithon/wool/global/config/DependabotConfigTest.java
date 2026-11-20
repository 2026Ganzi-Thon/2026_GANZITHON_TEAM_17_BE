package com.ganzithon.wool.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Dependabot 설정이 깨지지 않았고, 업데이트 PR이 모두 develop으로 가는지 확인한다.
// main으로는 develop에서만 PR을 받는 규칙(branch-protection.yml) 때문에, main으로 가는 설정이면 PR이 계속 막힌다.
class DependabotConfigTest {

    private static final String PATH = ".github/dependabot.yml";

    @Test
    void 설정_파일이_읽히고_세_가지_대상을_모두_develop으로_보낸다() throws IOException {
        PropertySource<?> config = new YamlPropertySourceLoader().load("dependabot", new FileSystemResource(PATH)).get(0);

        assertThat(config.getProperty("version")).isEqualTo(2);
        List<Object> ecosystems = new ArrayList<>();
        for (int i = 0; config.getProperty("updates[" + i + "].package-ecosystem") != null; i++) {
            ecosystems.add(config.getProperty("updates[" + i + "].package-ecosystem"));
            assertThat(config.getProperty("updates[" + i + "].target-branch")).isEqualTo("develop");
            assertThat(config.getProperty("updates[" + i + "].schedule.interval")).isEqualTo("weekly");
        }
        assertThat(ecosystems).containsExactlyInAnyOrder("gradle", "github-actions", "docker");
    }
}
