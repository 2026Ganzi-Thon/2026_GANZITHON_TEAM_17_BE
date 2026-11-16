package com.ganzithon.wool.report.repository;

import com.ganzithon.wool.report.entity.WorryReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorryReportRepository extends JpaRepository<WorryReport, Long> {
    boolean existsByWorryIdAndReporterId(Long worryId, String reporterId);
}
