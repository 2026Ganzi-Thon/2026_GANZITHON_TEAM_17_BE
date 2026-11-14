package com.ganzithon.wool.reply.entity;

// 털 단계. 누적 답장 수(숨김·삭제 제외)로 정한다. (POL-WOL-02)
public enum WoolStage {
    BALL,    // 털실 뭉치 1~5통
    SCARF,   // 목도리 6~19통
    SWEATER; // 스웨터 20통~

    public static final int BALL_START = 1;
    public static final int SCARF_START = 6;
    public static final int SWEATER_START = 20;

    // 답장이 0통이면 단계가 없어 null이다.
    public static WoolStage of(long totalReplies) {
        if (totalReplies < BALL_START) {
            return null;
        }
        if (totalReplies < SCARF_START) {
            return BALL;
        }
        if (totalReplies < SWEATER_START) {
            return SCARF;
        }
        return SWEATER;
    }
}
