package com.ganzithon.wool.reply.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 어르신의 "내가 보낸 답장" 목록용 조회. 답장마다 공감 수, 고마움·후일담 여부를 한 번의 쿼리로 가져온다. (답장 수만큼 쿼리가 늘지 않는다)
// 공감(reply_likes)은 JPA 엔티티가 없어서 고민 상세 조회(WorryDetailExtrasRepository)처럼 SQL로 읽는다.
@Repository
@RequiredArgsConstructor
public class SeniorReplyListRepository {

    private final JdbcTemplate jdbc;

    // worryTitle은 고민이 삭제됐거나 신고로 숨겨졌으면 null이다. 그 처리는 서비스가 한다.
    public record Row(Long replyId, Long worryId, String worryTitle, boolean worryVisible, String type,
                      long likeCount, boolean thanked, boolean hasFollowUp, LocalDateTime createdAt) {
    }

    // 삭제하거나 신고로 숨긴 답장은 뺀다. 최신순이고, 같은 시각이면 나중에 쓴 답장이 먼저다.
    public List<Row> findBySeniorId(String seniorId) {
        return jdbc.query("""
                SELECT r.id AS reply_id, r.worry_id, r.type, r.created_at,
                       w.title AS worry_title,
                       CASE WHEN w.deleted_at IS NULL AND w.hidden = FALSE THEN 1 ELSE 0 END AS worry_visible,
                       (SELECT COUNT(*) FROM reply_likes l WHERE l.reply_id = r.id) AS like_count,
                       CASE WHEN EXISTS (SELECT 1 FROM thanks t WHERE t.reply_id = r.id) THEN 1 ELSE 0 END AS thanked,
                       CASE WHEN EXISTS (SELECT 1 FROM follow_ups f WHERE f.reply_id = r.id) THEN 1 ELSE 0 END AS has_follow_up
                FROM replies r JOIN worries w ON w.id = r.worry_id
                WHERE r.senior_id = ? AND r.hidden = FALSE AND r.deleted_at IS NULL
                ORDER BY r.created_at DESC, r.id DESC
                """, (rs, rowNum) -> new Row(
                rs.getLong("reply_id"), rs.getLong("worry_id"), rs.getString("worry_title"),
                rs.getInt("worry_visible") == 1, rs.getString("type"), rs.getLong("like_count"),
                rs.getInt("thanked") == 1, rs.getInt("has_follow_up") == 1,
                rs.getTimestamp("created_at").toLocalDateTime()), seniorId);
    }
}
