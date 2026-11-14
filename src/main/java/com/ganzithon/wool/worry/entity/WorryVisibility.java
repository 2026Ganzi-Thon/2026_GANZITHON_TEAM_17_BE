package com.ganzithon.wool.worry.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorryVisibility {
    SENIORS_ONLY, PUBLIC;

    @JsonCreator
    public static WorryVisibility fromValue(String value) {
        return valueOf(value);
    }
}
