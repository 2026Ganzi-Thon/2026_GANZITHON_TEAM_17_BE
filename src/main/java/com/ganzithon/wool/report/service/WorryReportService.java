package com.ganzithon.wool.report.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.report.entity.WorryReport;
import com.ganzithon.wool.report.entity.WorryReportReason;
import com.ganzithon.wool.report.exception.ReportErrorCode;
import com.ganzithon.wool.report.repository.WorryReportRepository;
import com.ganzithon.wool.report.response.ReportWorryResponse;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorryReportService {
    private final WorryRepository worries;
    private final WorryReportRepository reports;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportWorryResponse report(User reporter, Long worryId, WorryReportReason reason) {
        var worry = worries.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        if (reporter.getRole() == Role.YOUTH && worry.getAuthor().getId().equals(reporter.getId())) {
            throw new BusinessException(ReportErrorCode.SELF_REPORT);
        }
        if (reports.existsByWorryIdAndReporterId(worryId, reporter.getId())) {
            throw new BusinessException(ReportErrorCode.WORRY_ALREADY_REPORTED);
        }
        if (worry.isHidden()) throw new BusinessException(WorryErrorCode.NOT_FOUND);
        if (reporter.getRole() == Role.YOUTH && worry.getVisibility() != WorryVisibility.PUBLIC) {
            throw new BusinessException(WorryErrorCode.FORBIDDEN_VIEW);
        }
        reports.save(WorryReport.of(worry, reporter, reason));
        worry.hide();
        return new ReportWorryResponse(worryId, true);
    }
}
