package com.ganzithon.wool.reply.entity;

// 털 단계. 누적 답장 수(숨김·삭제 제외)로 정한다. (POL-WOL-02)
public enum WoolStage {
    BALL,    // 털실 뭉치 1~5통
    SCARF,   // 목도리 6~19통
    SWEATER; // 스웨터 20통~

    // 답장이 0통이면 단계가 없어 null이다.
    public static WoolStage of(long totalReplies) {
        if (totalReplies <= 0) {
            return null;
        }
        if (totalReplies <= 5) {
            return BALL;
        }
        if (totalReplies <= 19) {
            return SCARF;
        }
        return SWEATER;
    }
}
