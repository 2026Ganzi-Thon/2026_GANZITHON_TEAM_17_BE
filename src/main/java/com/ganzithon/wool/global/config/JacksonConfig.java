package com.ganzithon.wool.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 응답의 시각은 항상 서울 기준 +09:00이 붙은 ISO 8601로 내보낸다. (예: 2026-11-04T20:10:00+09:00)
// 엔티티의 LocalDateTime은 서울 시각으로 간주한다. 서버 JVM 시간대도 Asia/Seoul이어야 저장되는 시각이 맞는다.
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule seoulLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("SeoulLocalDateTimeModule");
        module.addSerializer(LocalDateTime.class, new SeoulLocalDateTimeSerializer());
        return module;
    }

    static class SeoulLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext context) {
            gen.writeString(value.atZone(TimeConfig.SERVICE_ZONE)
                    .toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
