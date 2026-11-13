package com.ganzithon.wool.reply.repository;

import com.ganzithon.wool.reply.entity.Reply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplyRepository extends JpaRepository<Reply, Long> {

    // "유효 답장" = 삭제되지 않았고 신고로 숨겨지지 않은 답장. 화면에 보이는 답장 수, 고민 수정 가능 여부,
    // 어르신 목록의 0통 정렬이 모두 이 정의를 쓴다. 조건을 바꿀 때는 이 파일의 두 쿼리를 함께 바꾼다.
    @Query("select count(r) from Reply r where r.worry.id = :worryId and r.hidden = false and r.deletedAt is null")
    long countVisibleByWorryId(@Param("worryId") Long worryId);

    @Query("select count(r) > 0 from Reply r where r.worry.id = :worryId and r.hidden = false and r.deletedAt is null")
    boolean existsVisibleByWorryId(@Param("worryId") Long worryId);

    // 재답장 방지용. 삭제·숨김 답장도 포함해서 센다. 유효 답장 조건과 섞어 쓰지 않는다. (uk_replies_worry_senior)
    boolean existsByWorryIdAndSeniorId(Long worryId, String seniorId);
}
