package com.ganzithon.wool.user.request;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import jakarta.validation.constraints.NotNull;

// 닉네임은 요청에서 받지 않고 서버가 자동으로 할당한다. 10대는 AgeGroup에 없으므로 받지 않는다.
public record JoinRequest(
        @NotNull Role role,
        @NotNull AgeGroup ageGroup
) {
}
