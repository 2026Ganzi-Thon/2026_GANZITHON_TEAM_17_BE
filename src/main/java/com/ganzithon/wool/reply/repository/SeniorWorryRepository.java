package com.ganzithon.wool.reply.repository;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 어르신이 고민을 고르고 읽을 때 쓰는 조회. 고민 저장소(WorryRepository)의 잠금 조회와 섞이지 않도록 따로 둔다.
public interface SeniorWorryRepository extends Repository<Worry, Long> {

    interface WorryRow {
        Long getWorryId();
        String getTitle();
        WorryTag getTag();
        AgeGroup getAuthorAgeGroup();
        WorryVisibility getVisibility();
        LocalDateTime getCreatedAt();
        // 삭제·숨김 답장을 뺀 답장 수. "아직 답장이 없어요"와 정렬 기준이다.
        long getReplyCount();
        // 내가 쓴 답장 수. 삭제·숨김 답장도 센다. (한 어르신은 같은 고민에 한 번만 답장할 수 있다)
        long getMyReplyCount();
    }

    // 신고로 숨겨졌거나 삭제된 고민은 보이지 않는다. 정렬은 서비스에서 한다.
    @Query("select w.id as worryId, w.title as title, w.tag as tag, a.ageGroup as authorAgeGroup, "
            + "w.visibility as visibility, w.createdAt as createdAt, "
            + "(select count(r) from Reply r where r.worry = w and " + ReplyRepository.VISIBLE_REPLY + ") as replyCount, "
            + "(select count(m) from Reply m where m.worry = w and m.senior.id = :seniorId) as myReplyCount "
            + "from Worry w join w.author a where w.deletedAt is null and w.hidden = false")
    List<WorryRow> findReadableRows(@Param("seniorId") String seniorId);

    Optional<Worry> findByIdAndDeletedAtIsNullAndHiddenFalse(Long id);
}
