package com.ganzithon.wool.report.service;

import com.ganzithon.wool.user.entity.SanctionLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SanctionPolicyTest {

    @Test
    void 누적_신고_수가_1_3_5가_되는_순간에만_단계가_바뀐다() {
        assertThat(SanctionPolicy.levelReachedAt(1)).isEqualTo(SanctionLevel.WARNED);
        assertThat(SanctionPolicy.levelReachedAt(3)).isEqualTo(SanctionLevel.RESTRICTED);
        assertThat(SanctionPolicy.levelReachedAt(5)).isEqualTo(SanctionLevel.BANNED);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2, 4, 6, 7, 100})
    void 그_밖의_수에서는_단계가_바뀌지_않는다(long count) {
        assertThat(SanctionPolicy.levelReachedAt(count)).isNull();
    }
}
