package com.ganzithon.wool.certificate.repository;

import com.ganzithon.wool.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    boolean existsBySeniorIdAndReplyCount(String seniorId, int replyCount);

    // 발급일 최신순이다. 발급 시각이 같으면 나중에 만든 감사장이 먼저다.
    List<Certificate> findBySeniorIdOrderByIssuedAtDescIdDesc(String seniorId);
}
