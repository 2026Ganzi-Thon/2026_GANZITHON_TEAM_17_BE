package com.ganzithon.wool.reply.repository;

import com.ganzithon.wool.reply.entity.Reply;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    // "유효 답장" = 삭제되지 않았고 신고로 숨겨지지 않은 답장. 화면에 보이는 답장 수, 고민 수정 가능 여부,
    // 어르신 목록의 0통 정렬이 모두 이 정의를 쓴다.
    String VISIBLE_REPLY = "r.hidden = false and r.deletedAt is null";

    @Query("select count(r) from Reply r where r.worry.id = :worryId and " + VISIBLE_REPLY)
    long countVisibleByWorryId(@Param("worryId") Long worryId);

    @Query("select count(r) > 0 from Reply r where r.worry.id = :worryId and " + VISIBLE_REPLY)
    boolean existsVisibleByWorryId(@Param("worryId") Long worryId);

    interface VisibleReplySummary {
        Long getWorryId();
        long getReplyCount();
        long getUnreadCount();
    }

    @Query("select r.worry.id as worryId, count(r) as replyCount, "
            + "sum(case when r.firstViewedAt is null then 1 else 0 end) as unreadCount "
            + "from Reply r where r.worry.author.id = :authorId and r.worry.deletedAt is null and "
            + VISIBLE_REPLY + " group by r.worry.id")
    List<VisibleReplySummary> summarizeVisibleByAuthorId(@Param("authorId") String authorId);

    // 답장 행을 잠그고 가져온다. 삭제된 답장은 없는 것으로 본다. 답장을 바꾸는 쪽(삭제·수정, 고마움·신고)이 같은 잠금을 써서
    // "고마움을 받은 뒤 수정"이나 "삭제된 답장에 고마움"처럼 어긋난 결과가 생기지 않게 한다. 숨긴 답장도 가져온다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reply> findByIdAndDeletedAtIsNull(Long id);

    // 공개 고민을 읽는 어르신에게 보여줄 다른 선배의 답장. 내 답장과 삭제·숨김 답장은 뺀다. 댓글처럼 오래된 순이다.
    @Query("select r from Reply r join fetch r.senior where r.worry.id = :worryId and r.senior.id <> :seniorId and "
            + VISIBLE_REPLY + " order by r.createdAt asc, r.id asc")
    List<Reply> findVisibleByWorryIdExcludingSenior(@Param("worryId") Long worryId, @Param("seniorId") String seniorId);

    // 어르신의 누적 답장 수. 털 단계와 감사장 발급 기준이며, 숨김·삭제 답장은 제외한다. (POL-WOL-01)
    @Query("select count(r) from Reply r where r.senior.id = :seniorId and r.hidden = false and r.deletedAt is null")
    long countVisibleBySeniorId(@Param("seniorId") String seniorId);

    // 재답장 방지용. 삭제·숨김 답장도 포함해서 센다. 유효 답장 조건과 섞어 쓰지 않는다. (uk_replies_worry_senior)
    boolean existsByWorryIdAndSeniorId(Long worryId, String seniorId);
}
