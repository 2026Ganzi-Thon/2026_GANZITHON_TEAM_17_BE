package com.ganzithon.wool.reply.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ReplyLikeRepository {
    private final JdbcTemplate jdbc;
    public int delete(Long id, String user) {
        return jdbc.update("DELETE FROM reply_likes WHERE reply_id = ? AND user_id = ?", id, user);
    }
    public void insert(Long id, String user) {
        jdbc.update("INSERT INTO reply_likes (reply_id, user_id, created_at) VALUES (?, ?, ?)", id, user, LocalDateTime.now());
    }
    public long count(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reply_likes WHERE reply_id = ?", Long.class, id);
    }
}
