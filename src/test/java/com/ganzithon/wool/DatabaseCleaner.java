package com.ganzithon.wool;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// 전제: 테스트 DB(H2)에는 Flyway를 쓰지 않아 이력 테이블이 없고, 테스트는 병렬로 실행하지 않는다.
// 트랜잭션 없이 실제 커밋하는 테스트(동시성 테스트 등)가 남긴 데이터를 지운다.
// 테스트 DB(H2)는 클래스가 달라도 공유되어서, 남은 고민·답장이 다른 테스트의 "전체 목록" 검증을 흔들 수 있다.
@Component
public class DatabaseCleaner {

    @Autowired private JdbcTemplate jdbc;

    public void clean() {
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'",
                String.class);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : tables) {
                jdbc.execute("TRUNCATE TABLE \"" + table + "\"");
            }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
