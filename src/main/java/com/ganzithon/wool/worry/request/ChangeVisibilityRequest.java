package com.ganzithon.wool.worry.request;

import com.ganzithon.wool.worry.entity.WorryVisibility;
import jakarta.validation.constraints.NotNull;

public record ChangeVisibilityRequest(@NotNull WorryVisibility visibility) {
}
