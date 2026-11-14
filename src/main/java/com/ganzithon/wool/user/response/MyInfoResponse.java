package com.ganzithon.wool.user.response;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.SanctionLevel;
import com.ganzithon.wool.user.entity.User;

import java.time.LocalDateTime;

public record MyInfoResponse(String userId, Role role, String nickname, AgeGroup ageGroup, Sanction sanction) {

    public static MyInfoResponse from(User user) {
        Sanction sanction = user.isYouth()
                ? new Sanction(SanctionLevel.NONE, null)
                : new Sanction(user.getSanctionLevel(), user.getRestrictedUntil());
        return new MyInfoResponse(user.getId(), user.getRole(), user.getNickname(), user.getAgeGroup(), sanction);
    }

    public record Sanction(SanctionLevel level, LocalDateTime restrictedUntil) {
    }
}
