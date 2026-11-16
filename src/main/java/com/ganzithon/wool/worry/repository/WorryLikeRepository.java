package com.ganzithon.wool.worry.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class WorryLikeRepository {
    private final JdbcTemplate jdbc;

    public int delete(Long worryId, String userId) {
        return jdbc.update("DELETE FROM worry_likes WHERE worry_id = ? AND user_id = ?", worryId, userId);
    }

    public void insert(Long worryId, String userId) {
        jdbc.update("INSERT INTO worry_likes (worry_id, user_id, created_at) VALUES (?, ?, ?)",
                worryId, userId, LocalDateTime.now());
    }

    public long count(Long worryId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM worry_likes WHERE worry_id = ?", Long.class, worryId);
    }
}
