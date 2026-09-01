package com.gym.billing.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueReportResponse(
        Integer month,
        Integer year,
        BigDecimal totalCashCollected,
        BigDecimal totalAccrualRecognized,
        BigDecimal totalDeferredRevenue,
        List<RevenueScheduleResponse> schedules
) {}
