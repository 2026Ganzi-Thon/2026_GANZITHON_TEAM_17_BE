package com.ganzithon.wool.reply.response;

import java.time.LocalDateTime;

// REPLY_403_002 응답의 data. 영구 제한이면 restrictedUntil은 null이다.
public record RestrictedData(LocalDateTime restrictedUntil) {
}
