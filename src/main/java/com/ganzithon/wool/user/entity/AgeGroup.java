package com.ganzithon.wool.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

// API와 DB의 값은 "20S" 처럼 숫자로 시작해서 자바 상수 이름으로 쓸 수 없다.
// 상수는 AGE_*로 두고, 외부에는 value를 쓴다. (JSON: @JsonValue, DB: AgeGroupConverter)
@Getter
@RequiredArgsConstructor
public enum AgeGroup {
    AGE_20S("20S"),
    AGE_30S("30S"),
    AGE_40S("40S"),
    AGE_50S("50S"),
    AGE_60S("60S"),
    AGE_70S_PLUS("70S_PLUS");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AgeGroup from(String value) {
        return Arrays.stream(values())
                .filter(ageGroup -> ageGroup.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 나이대입니다: " + value));
    }
}
