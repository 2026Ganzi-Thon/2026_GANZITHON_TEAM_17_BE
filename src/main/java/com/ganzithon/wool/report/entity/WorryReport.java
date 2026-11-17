package com.ganzithon.wool.report.entity;

import com.ganzithon.wool.global.entity.BaseCreatedEntity;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
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

@Entity
@Table(name = "worry_reports", uniqueConstraints = @UniqueConstraint(name = "uk_worry_reports", columnNames = {"worry_id", "reporter_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorryReport extends BaseCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worry_id", nullable = false, updatable = false)
    private Worry worry;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "ENUM('AD', 'ABUSE', 'ETC')")
    private WorryReportReason reason;

    public static WorryReport of(Worry worry, User reporter, WorryReportReason reason) {
        if (worry == null || reporter == null || reason == null) throw new IllegalArgumentException("고민, 신고자, 사유는 필수입니다.");
        WorryReport report = new WorryReport();
        report.worry = worry;
        report.reporter = reporter;
        report.reason = reason;
        return report;
    }
}
