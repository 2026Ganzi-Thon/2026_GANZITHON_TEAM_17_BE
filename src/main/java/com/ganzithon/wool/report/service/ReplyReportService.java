package com.ganzithon.wool.report.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.service.NotificationService;
import com.ganzithon.wool.report.entity.ReplyReport;
import com.ganzithon.wool.report.entity.ReplyReportReason;
import com.ganzithon.wool.report.exception.ReportErrorCode;
import com.ganzithon.wool.report.repository.ReplyReportRepository;
import com.ganzithon.wool.report.response.ReportReplyResponse;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.service.SeniorLock;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 답장 신고(REPORT-001). 신고를 저장하고, 답장을 즉시 숨기고, 어르신의 누적 신고 수에 따라 제재 단계를 올린다.
// 숨김은 영구이고 관리자 검토는 없다. 신고 저장, 숨김, 제재, 제재 알림이 한 트랜잭션이다.
//
// 잠금 순서: 답장 행 → 어르신 행. 답장 삭제와 같고, 답장 작성(고민 행 → 어르신 행)과도 어긋나지 않는다.
// 숨김이 누적 답장 수를 줄이고 신고 수가 제재 단계를 바꾸므로, 둘 다 같은 어르신 기준으로 한 번에 하나씩 처리한다.
@Service
@RequiredArgsConstructor
public class ReplyReportService {

    private final ReplyRepository replyRepository;
    private final ReplyReportRepository replyReportRepository;
    private final SeniorLock seniorLock;
    private final NotificationService notificationService;

    // 어르신의 누적 답장 수와 감사장 기준을 잠금을 잡은 뒤에 읽으므로 READ COMMITTED로 실행한다.
    // MySQL 기본(REPEATABLE READ)에서는 트랜잭션의 첫 일반 조회가 그 시점의 스냅샷을 만들어서, 잠금을 기다린 뒤에도
    // 다른 트랜잭션이 그 사이 커밋한 답장·신고를 못 보는 오래된 수를 읽을 수 있다. READ COMMITTED는 조회마다 최신 커밋을 본다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportReplyResponse report(User reporter, Long replyId, ReplyReportReason reason) {
        // 삭제된 답장은 없는 것으로 본다. 숨긴 답장은 가져온다. (이미 신고했다면 아래에서 409가 된다)
        Reply reply = replyRepository.findByIdAndDeletedAtIsNull(replyId)
                .orElseThrow(() -> new BusinessException(ReplyErrorCode.NOT_FOUND));
        // 자기 고민에 달린 답장만 신고할 수 있다.
        if (!reply.getWorry().getAuthor().getId().equals(reporter.getId())) {
            throw new BusinessException(ReplyErrorCode.FORBIDDEN_VIEW);
        }
        if (replyReportRepository.existsByReplyIdAndReporterId(replyId, reporter.getId())) {
            throw new BusinessException(ReportErrorCode.ALREADY_REPORTED);
        }

        User senior = seniorLock.lock(reply.getSenior().getId());
        LocalDateTime now = LocalDateTime.now();
        long before = replyReportRepository.countDistinctReportersBySeniorId(senior.getId());

        replyReportRepository.saveAndFlush(ReplyReport.of(reply, reporter, reason));
        reply.hide();

        long after = replyReportRepository.countDistinctReportersBySeniorId(senior.getId());
        if (after > before) {
            applySanction(senior, after, now);
        }
        return new ReportReplyResponse(reply.getId(), true);
    }

    // 누적 신고 수가 1, 3, 5가 된 순간에만 단계를 올리고 알림을 보낸다. 이미 더 높은 단계면 내리지 않는다.
    private void applySanction(User senior, long distinctReporters, LocalDateTime now) {
        SanctionLevel level = SanctionPolicy.levelReachedAt(distinctReporters);
        if (level == null || level.ordinal() <= senior.getSanctionLevel().ordinal()) {
            return;
        }
        LocalDateTime restrictedUntil = level == SanctionLevel.RESTRICTED
                ? now.plusDays(SanctionPolicy.RESTRICTION_DAYS) : null;
        senior.applySanction(level, restrictedUntil);
        notificationService.send(senior.getId(), NotificationType.SANCTION, title(level), content(level),
                TargetKind.SANCTION, null);
    }

    private static String title(SanctionLevel level) {
        return switch (level) {
            case WARNED -> "답장에 대한 경고가 있어요";
            case RESTRICTED -> "7일 동안 답장을 쓸 수 없어요";
            case BANNED -> "더 이상 답장을 쓸 수 없어요";
            case NONE -> throw new IllegalArgumentException("제재가 없는 단계는 알림을 보내지 않습니다.");
        };
    }

    private static String content(SanctionLevel level) {
        return switch (level) {
            case WARNED -> "청년이 답장을 신고했어요. 서로를 존중하는 표현으로 답장해 주세요.";
            case RESTRICTED -> "신고가 쌓여 7일 동안 답장을 쓸 수 없어요. 기간이 지나면 다시 쓸 수 있어요.";
            case BANNED -> "신고가 계속 쌓여 답장 기능을 더 이상 쓸 수 없어요.";
            case NONE -> throw new IllegalArgumentException("제재가 없는 단계는 알림을 보내지 않습니다.");
        };
    }
}
