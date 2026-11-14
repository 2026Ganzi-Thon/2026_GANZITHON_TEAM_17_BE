package com.ganzithon.wool.report.repository;

import com.ganzithon.wool.report.entity.ReplyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplyReportRepository extends JpaRepository<ReplyReport, Long> {

    boolean existsByReplyIdAndReporterId(Long replyId, String reporterId);

    // 어르신의 누적 신고 수. 신고한 청년을 기준으로 중복을 뺀다. 삭제하거나 숨긴 답장의 신고도 센다. (POL-RPT-05, POL-RPL-09)
    @Query("select count(distinct rr.reporter.id) from ReplyReport rr where rr.reply.senior.id = :seniorId")
    long countDistinctReportersBySeniorId(@Param("seniorId") String seniorId);
}
