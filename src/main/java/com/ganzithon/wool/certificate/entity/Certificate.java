package com.ganzithon.wool.certificate.entity;

import com.ganzithon.wool.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

// 감사장. 어르신당 같은 기준(20, 40, 60...)은 한 번만 발급한다. (uk_certificates_senior_count)
@Entity
@Table(name = "certificates",
        uniqueConstraints = @UniqueConstraint(name = "uk_certificates_senior_count", columnNames = {"senior_id", "reply_count"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certificate {

    public static final int REPLY_COUNT_UNIT = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false, updatable = false)
    private User senior;

    // 발급 기준 답장 수 (20, 40, 60...)
    @Column(name = "reply_count", nullable = false, updatable = false)
    private int replyCount;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    private Certificate(User senior, int replyCount, LocalDateTime issuedAt) {
        this.senior = senior;
        this.replyCount = replyCount;
        this.issuedAt = issuedAt;
    }

    public static Certificate issue(User senior, int replyCount, LocalDateTime issuedAt) {
        if (senior == null || issuedAt == null) {
            throw new IllegalArgumentException("어르신과 발급 시각은 필수입니다.");
        }
        if (replyCount <= 0 || replyCount % REPLY_COUNT_UNIT != 0) {
            throw new IllegalArgumentException("감사장 기준 답장 수는 " + REPLY_COUNT_UNIT + "의 배수여야 합니다.");
        }
        return new Certificate(senior, replyCount, issuedAt);
    }

    public boolean isOwnedBy(String userId) {
        return senior.getId().equals(userId);
    }
}
