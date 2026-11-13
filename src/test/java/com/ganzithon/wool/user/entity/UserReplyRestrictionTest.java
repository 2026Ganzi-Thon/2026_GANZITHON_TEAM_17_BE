package com.ganzithon.wool.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserReplyRestrictionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 12, 0);

    private User senior() {
        return User.join(Role.SENIOR, AgeGroup.AGE_60S, "선배");
    }

    @Test
    void 제재가_없거나_경고만_받았으면_막지_않는다() {
        User user = senior();
        assertThat(user.isReplyRestrictedAt(NOW)).isFalse();

        user.applySanction(SanctionLevel.WARNED, null);
        assertThat(user.isReplyRestrictedAt(NOW)).isFalse();
    }

    @Test
    void 영구_제한은_항상_막는다() {
        User user = senior();
        user.applySanction(SanctionLevel.BANNED, null);

        assertThat(user.isReplyRestrictedAt(NOW)).isTrue();
        assertThat(user.isReplyRestrictedAt(NOW.plusYears(10))).isTrue();
    }

    @Test
    void 일주일_제한은_해제_시각_전까지만_막는다() {
        User user = senior();
        user.applySanction(SanctionLevel.RESTRICTED, NOW.plusDays(7));

        assertThat(user.isReplyRestrictedAt(NOW)).isTrue();
        assertThat(user.isReplyRestrictedAt(NOW.plusDays(7).minusSeconds(1))).isTrue();
        assertThat(user.isReplyRestrictedAt(NOW.plusDays(7))).isFalse();
        assertThat(user.isReplyRestrictedAt(NOW.plusDays(8))).isFalse();
    }

    @Test
    void 해제_시각이_없는_일주일_제한은_안전하게_막는다() {
        User user = senior();
        user.applySanction(SanctionLevel.RESTRICTED, null);

        assertThat(user.isReplyRestrictedAt(NOW)).isTrue();
    }

    @Test
    void 제한을_풀면_막지_않는다() {
        User user = senior();
        user.applySanction(SanctionLevel.RESTRICTED, NOW.plusDays(7));
        user.liftRestriction();

        assertThat(user.isReplyRestrictedAt(NOW)).isFalse();
    }
}
