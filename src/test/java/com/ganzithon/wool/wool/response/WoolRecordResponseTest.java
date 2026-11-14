package com.ganzithon.wool.wool.response;

import com.ganzithon.wool.reply.entity.WoolStage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class WoolRecordResponseTest {

    // 누적, 단계, 스웨터 수, 다음 단계, 다음 단계까지 남은 수, 진행률
    @ParameterizedTest
    @CsvSource(nullValues = "NONE", value = {
            "0,  NONE,    0, BALL,    1,  0.0",
            "1,  BALL,    0, SCARF,   5,  0.0",
            "3,  BALL,    0, SCARF,   3,  0.4",
            "5,  BALL,    0, SCARF,   1,  0.8",
            "6,  SCARF,   0, SWEATER, 14, 0.0",
            "8,  SCARF,   0, SWEATER, 12, 0.14",
            "12, SCARF,   0, SWEATER, 8,  0.43",
            "19, SCARF,   0, SWEATER, 1,  0.93",
            "20, SWEATER, 1, SWEATER, 20, 0.0",
            "25, SWEATER, 1, SWEATER, 15, 0.25",
            "39, SWEATER, 1, SWEATER, 1,  0.95",
            "40, SWEATER, 2, SWEATER, 20, 0.0",
            "41, SWEATER, 2, SWEATER, 19, 0.05",
            "60, SWEATER, 3, SWEATER, 20, 0.0"
    })
    void 누적_답장_수로_단계와_남은_수와_진행률을_계산한다(long total, WoolStage stage, long sweaterCount, WoolStage nextStage,
                                    long remaining, double progress) {
        WoolRecordResponse record = WoolRecordResponse.from(total);

        assertThat(record.totalReplies()).isEqualTo(total);
        assertThat(record.stage()).isEqualTo(stage);
        assertThat(record.sweaterCount()).isEqualTo(sweaterCount);
        assertThat(record.nextStage()).isEqualTo(nextStage);
        assertThat(record.remainingToNext()).isEqualTo(remaining);
        assertThat(record.progress()).isEqualTo(progress);
    }

    @ParameterizedTest
    @CsvSource({"-1", "-100"})
    void 음수는_0통으로_본다(long total) {
        WoolRecordResponse record = WoolRecordResponse.from(total);

        assertThat(record.totalReplies()).isZero();
        assertThat(record.stage()).isNull();
        assertThat(record.nextStage()).isEqualTo(WoolStage.BALL);
        assertThat(record.remainingToNext()).isEqualTo(1);
        assertThat(record.progress()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"1", "5", "6", "19", "20", "21", "39", "40", "1000"})
    void 진행률은_항상_0이상_1미만이고_남은_수는_1이상이다(long total) {
        WoolRecordResponse record = WoolRecordResponse.from(total);

        assertThat(record.progress()).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
        assertThat(record.remainingToNext()).isGreaterThanOrEqualTo(1);
    }
}
