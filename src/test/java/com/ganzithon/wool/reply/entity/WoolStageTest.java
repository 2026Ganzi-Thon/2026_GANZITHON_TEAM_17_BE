package com.ganzithon.wool.reply.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WoolStageTest {

    @Test
    void 답장이_없으면_단계가_없다() {
        assertThat(WoolStage.of(0)).isNull();
        assertThat(WoolStage.of(-1)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"1,BALL", "5,BALL", "6,SCARF", "19,SCARF", "20,SWEATER", "21,SWEATER", "40,SWEATER", "1000,SWEATER"})
    void 통수에_따라_단계가_정해진다(long total, WoolStage expected) {
        assertThat(WoolStage.of(total)).isEqualTo(expected);
    }
}
