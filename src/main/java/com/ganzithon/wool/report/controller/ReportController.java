package com.ganzithon.wool.report.controller;

import com.ganzithon.wool.global.auth.LoginUser;
import com.ganzithon.wool.global.response.ApiResponse;
import com.ganzithon.wool.report.request.ReportReplyRequest;
import com.ganzithon.wool.report.response.ReportReplyResponse;
import com.ganzithon.wool.report.response.ReportResponseCode;
import com.ganzithon.wool.report.service.ReplyReportService;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReplyReportService replyReportService;

    // REPORT-001 답장 신고
    @PostMapping("/replies/{replyId}/reports")
    public ResponseEntity<ApiResponse<ReportReplyResponse>> reportReply(
            @LoginUser(Role.YOUTH) User youth, @PathVariable Long replyId,
            @Valid @RequestBody ReportReplyRequest request) {
        return ApiResponse.of(ReportResponseCode.REPLY_REPORTED,
                replyReportService.report(youth, replyId, request.reason())).toResponseEntity();
    }
}
