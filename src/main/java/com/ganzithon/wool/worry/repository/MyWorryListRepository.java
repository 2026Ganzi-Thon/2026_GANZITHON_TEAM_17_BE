package com.ganzithon.wool.worry.repository;

import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.response.MyWorryListResponse.Item;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyWorryListRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<Item> findByAuthorId(String authorId) {
        return jdbcTemplate.query("""
                SELECT w.id, w.title, w.tag, w.visibility, w.hidden, w.created_at,
                       COUNT(r.id) AS reply_count,
                       COALESCE(MAX(CASE WHEN r.id IS NOT NULL AND r.first_viewed_at IS NULL THEN 1 ELSE 0 END), 0) AS has_unread_reply
                  FROM worries w
                  LEFT JOIN replies r ON r.worry_id = w.id AND r.hidden = FALSE AND r.deleted_at IS NULL
                 WHERE w.author_id = ? AND w.deleted_at IS NULL
                 GROUP BY w.id, w.title, w.tag, w.visibility, w.hidden, w.created_at
                 ORDER BY w.created_at DESC, w.id DESC
                """, (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("title"),
                WorryTag.valueOf(rs.getString("tag")), WorryVisibility.valueOf(rs.getString("visibility")),
                rs.getLong("reply_count"), rs.getBoolean("has_unread_reply"), rs.getBoolean("hidden"),
                rs.getTimestamp("created_at").toLocalDateTime()), authorId);
    }
}
