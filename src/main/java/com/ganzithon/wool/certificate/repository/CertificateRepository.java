package com.ganzithon.wool.certificate.repository;

import com.ganzithon.wool.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    boolean existsBySeniorIdAndReplyCount(String seniorId, int replyCount);
}
