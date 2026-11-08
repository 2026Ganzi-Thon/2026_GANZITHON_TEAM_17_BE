package com.ganzithon.wool.filter.service;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// 클래스패스의 단어 목록 파일(한 줄에 하나, '#'으로 시작하는 줄은 주석)을 읽어 비교용으로 정규화한다.
final class KeywordList {

    private KeywordList() {
    }

    static List<String> load(String classpathLocation) {
        try (var input = new ClassPathResource(classpathLocation).getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return content.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(TextNormalizer::normalize)
                    .filter(word -> !word.isEmpty())
                    .distinct()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("단어 목록을 읽을 수 없습니다: " + classpathLocation, e);
        }
    }
}
