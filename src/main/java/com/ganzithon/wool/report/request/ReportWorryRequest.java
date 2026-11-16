package com.ganzithon.wool.report.request;

import com.ganzithon.wool.report.entity.WorryReportReason;
import jakarta.validation.constraints.NotNull;

public record ReportWorryRequest(@NotNull WorryReportReason reason) { }
