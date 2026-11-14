package com.ganzithon.wool.worry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class WorryDetailExtrasRepository {
    private final JdbcTemplate jdbc;

    public WorryDetailExtrasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Likes(long count, boolean mine) {}
    public record ReplyExtras(Long replyId, long likeCount, boolean likedByMe,
                              String followUpContent, String followUpVisibility, LocalDateTime followUpCreatedAt) {}

    public Likes worryLikes(Long worryId, String userId) {
        return jdbc.queryForObject("SELECT COUNT(*), COUNT(CASE WHEN user_id = ? THEN 1 END) FROM worry_likes WHERE worry_id = ?",
                (rs, row) -> new Likes(rs.getLong(1), rs.getLong(2) > 0), userId, worryId);
    }

    public List<ReplyExtras> replyExtras(Long worryId, String userId) {
        return jdbc.query("""
                SELECT r.id, (SELECT COUNT(*) FROM reply_likes l WHERE l.reply_id = r.id) AS like_count,
                       (SELECT COUNT(*) FROM reply_likes l WHERE l.reply_id = r.id AND l.user_id = ?) AS my_likes,
                       f.content, f.visibility, f.created_at
                FROM replies r LEFT JOIN follow_ups f ON f.reply_id = r.id
                WHERE r.worry_id = ? AND r.hidden = false AND r.deleted_at IS NULL
                """, (rs, row) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new ReplyExtras(rs.getLong("id"), rs.getLong("like_count"), rs.getLong("my_likes") > 0,
                    rs.getString("content"), rs.getString("visibility"), createdAt == null ? null : createdAt.toLocalDateTime());
        }, userId, worryId);
    }
}
