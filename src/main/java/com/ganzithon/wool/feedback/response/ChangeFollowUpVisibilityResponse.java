package com.ganzithon.wool.feedback.response;

import com.ganzithon.wool.worry.entity.WorryVisibility;

public record ChangeFollowUpVisibilityResponse(Long replyId, WorryVisibility visibility) { }
