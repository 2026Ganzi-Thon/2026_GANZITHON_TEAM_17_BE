package com.ganzithon.wool.feedback.request;

import com.ganzithon.wool.worry.entity.WorryVisibility;
import jakarta.validation.constraints.NotNull;

public record ChangeFollowUpVisibilityRequest(@NotNull WorryVisibility visibility) { }
