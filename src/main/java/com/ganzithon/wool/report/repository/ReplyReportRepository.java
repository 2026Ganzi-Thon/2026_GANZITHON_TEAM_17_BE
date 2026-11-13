package com.ganzithon.wool.report.repository;

import com.ganzithon.wool.report.entity.ReplyReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyReportRepository extends JpaRepository<ReplyReport, Long> {

    boolean existsByReplyIdAndReporterId(Long replyId, String reporterId);
}
