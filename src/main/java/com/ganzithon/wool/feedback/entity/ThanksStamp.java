package com.ganzithon.wool.feedback.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ThanksStamp {
    GRATEFUL, HELPED, CHEERED;

    @JsonCreator
    public static ThanksStamp fromValue(String value) {
        return valueOf(value);
    }
}
