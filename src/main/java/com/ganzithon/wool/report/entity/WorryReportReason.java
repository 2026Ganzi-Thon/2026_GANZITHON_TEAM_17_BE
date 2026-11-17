package com.ganzithon.wool.report.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorryReportReason {
    AD, ABUSE, ETC;

    @JsonCreator
    public static WorryReportReason fromValue(String value) {
        return valueOf(value);
    }
}
