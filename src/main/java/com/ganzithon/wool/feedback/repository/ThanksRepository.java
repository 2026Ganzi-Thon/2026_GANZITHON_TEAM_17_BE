package com.ganzithon.wool.feedback.repository;

import com.ganzithon.wool.feedback.entity.Thanks;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThanksRepository extends JpaRepository<Thanks, Long> {
    boolean existsByReplyId(Long replyId);
    java.util.Optional<Thanks> findByReplyId(Long replyId);
}
