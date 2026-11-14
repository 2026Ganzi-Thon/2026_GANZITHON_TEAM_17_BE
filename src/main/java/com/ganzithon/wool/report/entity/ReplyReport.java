package com.ganzithon.wool.report.entity;

import com.ganzithon.wool.global.entity.BaseCreatedEntity;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 같은 청년은 같은 답장을 한 번만 신고할 수 있다. 제재 단계는 어르신 답장의 서로 다른 신고자 수로 계산한다.
@Entity
@Table(name = "reply_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_reply_reports", columnNames = {"reply_id", "reporter_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplyReport extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reply_id", nullable = false, updatable = false)
    private Reply reply;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false,
            columnDefinition = "ENUM('ABUSE', 'SEXUAL', 'AD', 'IRRELEVANT', 'ETC')")
    private ReplyReportReason reason;

    private ReplyReport(Reply reply, User reporter, ReplyReportReason reason) {
        this.reply = reply;
        this.reporter = reporter;
        this.reason = reason;
    }

    public static ReplyReport of(Reply reply, User reporter, ReplyReportReason reason) {
        if (reply == null || reporter == null || reason == null) {
            throw new IllegalArgumentException("답장, 신고한 청년, 신고 사유는 필수입니다.");
        }
        return new ReplyReport(reply, reporter, reason);
    }
}
