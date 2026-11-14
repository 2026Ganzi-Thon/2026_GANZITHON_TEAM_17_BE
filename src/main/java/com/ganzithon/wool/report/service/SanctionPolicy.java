package com.ganzithon.wool.report.service;

import com.ganzithon.wool.user.entity.SanctionLevel;

// 어르신 자동 단계 제재. (POL-RPT-04~06)
// 누적 신고 수는 신고한 청년 기준으로 중복을 뺀 수다. 같은 청년이 같은 어르신을 여러 번 신고해도 1회로 센다.
// 누적 수가 정확히 1, 3, 5가 되는 순간에만 단계가 바뀐다. (1회 경고 / 3회 7일 답장 제한 / 5회 영구 제한)
// 7일 제한이 풀린 뒤에도 누적 수는 유지하고, 4회째에는 다시 제한하지 않는다.
public final class SanctionPolicy {

    public static final int WARNING_AT = 1;
    public static final int RESTRICTION_AT = 3;
    public static final int BAN_AT = 5;
    public static final int RESTRICTION_DAYS = 7;

    private SanctionPolicy() {
    }

    // 누적 신고 수가 count가 되는 순간 적용할 단계. 단계가 바뀌지 않는 수면 null이다.
    public static SanctionLevel levelReachedAt(long count) {
        if (count == WARNING_AT) {
            return SanctionLevel.WARNED;
        }
        if (count == RESTRICTION_AT) {
            return SanctionLevel.RESTRICTED;
        }
        if (count == BAN_AT) {
            return SanctionLevel.BANNED;
        }
        return null;
    }
}
