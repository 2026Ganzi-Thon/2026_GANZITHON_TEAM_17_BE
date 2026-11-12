package com.ganzithon.wool.user.entity;

import com.ganzithon.wool.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseCreatedEntity {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false)
    private Role role;

    @Convert(converter = AgeGroupConverter.class)
    @Column(name = "age_group", nullable = false,
            columnDefinition = "ENUM('20S', '30S', '40S', '50S', '60S', '70S_PLUS')")
    private AgeGroup ageGroup;

    @Column(name = "nickname", length = 10, nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanction_level", nullable = false,
            columnDefinition = "ENUM('NONE', 'WARNED', 'RESTRICTED', 'BANNED')")
    private SanctionLevel sanctionLevel = SanctionLevel.NONE;

    @Column(name = "restricted_until")
    private LocalDateTime restrictedUntil;

    private User(Role role, AgeGroup ageGroup, String nickname) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.ageGroup = ageGroup;
        this.nickname = nickname;
    }

    public static User join(Role role, AgeGroup ageGroup, String nickname) {
        return new User(role, ageGroup, nickname);
    }

    // 검사(형식·금지어·중복)를 마친 닉네임만 넘긴다. 검사는 NicknameValidator가 한다.
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isYouth() {
        return role == Role.YOUTH;
    }

    public boolean isSenior() {
        return role == Role.SENIOR;
    }

    public void applySanction(SanctionLevel level, LocalDateTime restrictedUntil) {
        this.sanctionLevel = level;
        this.restrictedUntil = restrictedUntil;
    }

    // 7일 제한이 만료됐을 때. 누적 신고 수는 reply_reports에서 계산하므로 여기서는 상태만 푼다.
    public void liftRestriction() {
        this.sanctionLevel = SanctionLevel.NONE;
        this.restrictedUntil = null;
    }
}
