package com.ganzithon.wool.user.response;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;

public record JoinResponse(
        String userId,
        Role role,
        String nickname,
        AgeGroup ageGroup
) {

    public static JoinResponse from(User user) {
        return new JoinResponse(user.getId(), user.getRole(), user.getNickname(), user.getAgeGroup());
    }
}
