package com.gym.billing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Đối soát doanh thu theo ngày (E6) — Kế toán/Admin/Lễ tân cùng xem 1 báo cáo
 * duy nhất: hôm đó thu được bao nhiêu, qua những hợp đồng nào, đã xác nhận
 * đủ chưa. Kế toán xác nhận GỘP 1 lần, không cần duyệt từng dòng.
 */
public record DailyRevenueResponse(
        LocalDate date,
        /** Tổng thực thu ròng (đã trừ hoàn tiền). */
        BigDecimal totalAmount,
        /** Phần đã được Kế toán xác nhận. */
        BigDecimal confirmedAmount,
        /** Phần còn lại CHƯA xác nhận — 0 nghĩa là đã xác nhận đủ. */
        BigDecimal pendingAmount,
        boolean daXacNhanDuDoanhThuTrongNgay,
        List<PaymentLineResponse> lines
) {}
