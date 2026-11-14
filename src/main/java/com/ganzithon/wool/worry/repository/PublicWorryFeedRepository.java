package com.ganzithon.wool.worry.repository;

import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.response.PublicWorryFeedResponse.Item;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PublicWorryFeedRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<Item> find(WorryTag tag) {
        String sql = """
                SELECT w.id, w.title, w.tag, w.created_at,
                       (SELECT COUNT(*) FROM replies r WHERE r.worry_id = w.id AND r.hidden = FALSE AND r.deleted_at IS NULL) AS reply_count,
                       (SELECT COUNT(*) FROM worry_likes l WHERE l.worry_id = w.id) AS like_count
                  FROM worries w
                 WHERE w.visibility = 'PUBLIC' AND w.hidden = FALSE AND w.deleted_at IS NULL
                """ + (tag == null ? "" : " AND w.tag = ? ") + " ORDER BY w.created_at DESC, w.id DESC";
        Object[] args = tag == null ? new Object[0] : new Object[]{tag.name()};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("title"),
                WorryTag.valueOf(rs.getString("tag")), rs.getLong("reply_count"), rs.getLong("like_count"),
                rs.getTimestamp("created_at").toLocalDateTime()), args);
    }
}
