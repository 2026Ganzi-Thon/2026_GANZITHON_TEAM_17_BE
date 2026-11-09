package com.ganzithon.wool.worry.response;

import java.time.LocalDateTime;

public record CreateWorryResponse(Long worryId, boolean crisisDetected, LocalDateTime createdAt) {
}
