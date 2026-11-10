package com.ganzithon.wool.worry.request;

import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorryRequest(
        @NotBlank @Size(max = 40) String title,
        @NotBlank @Size(max = 1000) String content,
        @NotNull WorryTag tag,
        @NotNull WorryVisibility visibility
) {
}
