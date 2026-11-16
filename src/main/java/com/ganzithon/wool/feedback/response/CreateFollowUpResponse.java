package com.ganzithon.wool.feedback.response;

import com.ganzithon.wool.worry.entity.WorryVisibility;
import java.time.LocalDateTime;

public record CreateFollowUpResponse(Long replyId, String content, WorryVisibility visibility, LocalDateTime createdAt) { }
