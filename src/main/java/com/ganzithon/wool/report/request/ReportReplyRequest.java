package com.ganzithon.wool.report.request;

import com.ganzithon.wool.report.entity.ReplyReportReason;
import jakarta.validation.constraints.NotNull;

// 사유는 ABUSE(욕설) / SEXUAL(성희롱) / AD(광고·홍보) / IRRELEVANT(관련 없는 내용) / ETC(기타) 중 하나다.
public record ReportReplyRequest(@NotNull ReplyReportReason reason) {
}
