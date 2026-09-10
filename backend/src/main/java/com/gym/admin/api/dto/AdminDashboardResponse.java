package com.gym.admin.api.dto;

import java.math.BigDecimal;

/**
 * Tổng quan cho Admin: hai nhóm ưu tiên đã chốt — Hội viên &amp; Doanh thu, và
 * Vận hành hôm nay. Nhóm PT &amp; CRM chưa làm, để dịp sau.
 */
public record AdminDashboardResponse(
        MembersOverview membersOverview,
        RevenueOverview revenueOverview,
        OperationsToday operationsToday
) {

    /** Hội viên đang hoạt động = có hợp đồng ACTIVE, KHÔNG phải COUNT(*) FROM members. */
    public record MembersOverview(
            long activeCount,
            long newThisMonthCount,
            long expiringSoonCount,
            long frozenCount
    ) {}

    /** Doanh thu tháng hiện tại — tái dùng {@code RevenueRecognitionService.getRevenueReport}. */
    public record RevenueOverview(
            BigDecimal cashCollectedThisMonth,
            BigDecimal accrualRecognizedThisMonth,
            BigDecimal deferredRevenueThisMonth,
            BigDecimal unpaidOverdueAmount,
            long unpaidOverdueCount
    ) {}

    public record OperationsToday(
            long checkInsToday,
            long deniedToday,
            long openCashShifts,
            long cashShiftDiscrepancies,
            long openFeedbacksCount,
            long urgentFeedbacksCount,
            long equipmentNeedsRepairCount,
            long draftPayrollRunsCount
    ) {}
}
