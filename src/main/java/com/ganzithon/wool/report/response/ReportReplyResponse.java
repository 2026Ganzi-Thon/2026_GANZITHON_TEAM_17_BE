package com.ganzithon.wool.report.response;

// hidden은 항상 true다. 신고한 답장은 즉시 숨긴다. 프론트는 내 고민 상세로 이동한다.
public record ReportReplyResponse(Long replyId, boolean hidden) {
}
