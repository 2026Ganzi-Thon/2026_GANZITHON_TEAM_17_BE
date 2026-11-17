package com.ganzithon.wool.reply.response;

import com.ganzithon.wool.reply.entity.WoolStage;

// REPLY-001. 어르신 홈 화면 요약.
// woolStage는 누적 답장이 0통이면 단계가 없어 null이다. (WOOL-001과 같다)
public record SeniorHomeResponse(String nickname, long newWorryCountToday, long unreadReceivedCount,
                                 WoolStage woolStage, long totalReplies) {
}
