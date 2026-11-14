package com.ganzithon.wool.wool.response;

import com.ganzithon.wool.reply.entity.WoolStage;

// WOOL-001 털 기록. 누적 답장 수 하나로 단계, 스웨터 수, 다음 단계까지 남은 수, 진행 막대 값을 계산한다.
//
// | 누적 | stage | nextStage | remainingToNext | progress                         |
// | 0통  | null  | BALL      | 1               | 0                                |
// | 1~5  | BALL  | SCARF     | 6 - 누적        | (누적 - 1) / 5                   |
// | 6~19 | SCARF | SWEATER   | 20 - 누적       | (누적 - 6) / 14                  |
// | 20~  | SWEATER | SWEATER | 20 - (누적 % 20) | (누적 % 20) / 20 (다음 스웨터까지) |
//
// progress는 "다음 단계까지"의 진행률(0~1)이고 소수 둘째 자리까지 반올림한다.
public record WoolRecordResponse(long totalReplies, WoolStage stage, long sweaterCount, WoolStage nextStage,
                                 long remainingToNext, double progress) {

    // 스웨터를 한 벌 완성하는 데 필요한 답장 수이자 스웨터 이후 단계가 반복되는 간격이다. (POL-WOL-03)
    private static final int SWEATER_INTERVAL = WoolStage.SWEATER_START;

    public static WoolRecordResponse from(long totalReplies) {
        long total = Math.max(totalReplies, 0);
        WoolStage stage = WoolStage.of(total);
        long sweaterCount = total / SWEATER_INTERVAL;

        if (stage == null) {
            return new WoolRecordResponse(total, null, sweaterCount, WoolStage.BALL, WoolStage.BALL_START - total, 0);
        }
        return switch (stage) {
            case BALL -> new WoolRecordResponse(total, stage, sweaterCount, WoolStage.SCARF,
                    WoolStage.SCARF_START - total,
                    ratio(total - WoolStage.BALL_START, WoolStage.SCARF_START - WoolStage.BALL_START));
            case SCARF -> new WoolRecordResponse(total, stage, sweaterCount, WoolStage.SWEATER,
                    WoolStage.SWEATER_START - total,
                    ratio(total - WoolStage.SCARF_START, WoolStage.SWEATER_START - WoolStage.SCARF_START));
            case SWEATER -> {
                long sinceLastSweater = total % SWEATER_INTERVAL;
                yield new WoolRecordResponse(total, stage, sweaterCount, WoolStage.SWEATER,
                        SWEATER_INTERVAL - sinceLastSweater, ratio(sinceLastSweater, SWEATER_INTERVAL));
            }
        };
    }

    private static double ratio(long done, long whole) {
        return Math.round((double) done / whole * 100) / 100.0;
    }
}
