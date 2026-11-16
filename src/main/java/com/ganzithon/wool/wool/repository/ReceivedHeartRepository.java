package com.ganzithon.wool.wool.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 어르신이 받은 마음(청년이 보낸 고마움과 후일담) 조회와 읽음 표시.
// 보이는 마음은 "삭제하거나 신고로 숨기지 않은 내 답장"에 달린 것이고, 그 답장이 달린 고민도 삭제되거나 숨겨지지 않아야 한다.
// (POL-RPL-09, POL-WRY-09) 안 읽은 수(어르신 홈)도 같은 조건으로 센다. 그래야 목록에 안 보이는 마음이 안 읽은 수에 남지 않는다.
@Repository
@RequiredArgsConstructor
public class ReceivedHeartRepository {

    private static final int IN_CHUNK = 500;

    private final JdbcTemplate jdbc;

    // 같은 조건을 쓰는 FROM/WHERE 조각이다. 고마움과 후일담 두 쪽에서 똑같이 쓴다.
    private static final String VISIBLE_FILTER = """
            JOIN replies r ON r.id = %1$s.reply_id
            JOIN worries w ON w.id = r.worry_id
            WHERE r.senior_id = ? AND r.hidden = FALSE AND r.deleted_at IS NULL
              AND w.deleted_at IS NULL AND w.hidden = FALSE
            """;

    public record Row(String kind, Long itemId, Long replyId, String worryTitle, String stamp, String content,
                      LocalDateTime createdAt) {
    }

    // 최신순이고 같은 시각이면 id가 큰 쪽이 먼저다. 읽음 표시를 위해 항목 id도 함께 돌려준다.
    public List<Row> findBySeniorId(String seniorId) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT 'THANKS' AS kind, t.id AS item_id, r.id AS reply_id, w.title AS worry_title,
                           t.stamp AS stamp, t.message AS content, t.created_at AS created_at
                    FROM thanks t
                """ + VISIBLE_FILTER.formatted("t") + """
                    UNION ALL
                    SELECT 'FOLLOW_UP' AS kind, f.id AS item_id, r.id AS reply_id, w.title AS worry_title,
                           NULL AS stamp, f.content AS content, f.created_at AS created_at
                    FROM follow_ups f
                """ + VISIBLE_FILTER.formatted("f") + """
                ) hearts
                ORDER BY created_at DESC, item_id DESC, kind
                """, (rs, rowNum) -> new Row(
                rs.getString("kind"), rs.getLong("item_id"), rs.getLong("reply_id"), rs.getString("worry_title"),
                rs.getString("stamp"), rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime()),
                seniorId, seniorId);
    }

    // 목록에 담아 보낸 마음만 읽음으로 바꾼다. "안 읽은 걸 전부"가 아니라 보낸 id만 바꾸므로,
    // 조회하는 사이 새로 도착한 마음은 안 읽은 채로 남는다. 이미 읽은 마음은 건드리지 않는다.
    public void markRead(List<Long> thanksIds, List<Long> followUpIds) {
        markRead("thanks", thanksIds);
        markRead("follow_ups", followUpIds);
    }

    private void markRead(String table, List<Long> ids) {
        for (List<Long> chunk : chunks(ids)) {
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            jdbc.update("UPDATE " + table + " SET read_by_senior = TRUE WHERE read_by_senior = FALSE AND id IN (" + placeholders + ")",
                    chunk.toArray());
        }
    }

    private static List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += IN_CHUNK) {
            result.add(ids.subList(i, Math.min(ids.size(), i + IN_CHUNK)));
        }
        return result;
    }
}
