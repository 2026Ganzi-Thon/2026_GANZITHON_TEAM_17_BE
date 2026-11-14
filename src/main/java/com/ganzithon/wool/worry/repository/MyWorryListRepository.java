package com.ganzithon.wool.worry.repository;

import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyWorryListRepository {
    private final JdbcTemplate jdbcTemplate;

    public record WorryRow(Long worryId, String title, WorryTag tag, WorryVisibility visibility,
                           boolean hidden, LocalDateTime createdAt) {
    }

    public List<WorryRow> findByAuthorId(String authorId) {
        return jdbcTemplate.query("""
                SELECT w.id, w.title, w.tag, w.visibility, w.hidden, w.created_at
                  FROM worries w
                 WHERE w.author_id = ? AND w.deleted_at IS NULL
                 ORDER BY w.created_at DESC, w.id DESC
                """, (rs, rowNum) -> new WorryRow(rs.getLong("id"), rs.getString("title"),
                WorryTag.valueOf(rs.getString("tag")), WorryVisibility.valueOf(rs.getString("visibility")),
                rs.getBoolean("hidden"), rs.getTimestamp("created_at").toLocalDateTime()), authorId);
    }
}
