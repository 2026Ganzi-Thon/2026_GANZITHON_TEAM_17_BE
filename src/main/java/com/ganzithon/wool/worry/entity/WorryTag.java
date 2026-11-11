package com.ganzithon.wool.worry.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorryTag {
    CAREER, LOVE, FAMILY, RELATIONSHIP, LIVING, MIND, HEALTH, OTHER;

    @JsonCreator
    public static WorryTag fromValue(String value) {
        return valueOf(value);
    }
}
