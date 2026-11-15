package com.ganzithon.wool.feedback.repository;

import com.ganzithon.wool.feedback.entity.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {
    boolean existsByReplyId(Long replyId);
    @Modifying
    @Query("update FollowUp f set f.visibility = com.ganzithon.wool.worry.entity.WorryVisibility.SENIORS_ONLY where f.reply.worry.id = :worryId")
    int makePrivateByWorryId(@Param("worryId") Long worryId);
}
