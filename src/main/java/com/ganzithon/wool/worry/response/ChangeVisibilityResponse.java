package com.ganzithon.wool.worry.response;

import com.ganzithon.wool.worry.entity.WorryVisibility;

public record ChangeVisibilityResponse(Long worryId, WorryVisibility visibility) {
}
