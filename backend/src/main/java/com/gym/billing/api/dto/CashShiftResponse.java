package com.gym.billing.api.dto;

import com.gym.billing.domain.CashShift;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Ca làm việc kèm kết quả đối soát tiền mặt. */
public record CashShiftResponse(
        Long id,
        Long employeeId,
        String employeeName,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        BigDecimal openingBalance,
        /** Số hệ thống tính ra là phải có trong két. */
        BigDecimal expectedCash,
        /** Số lễ tân đếm được. */
        BigDecimal countedCash,
        /** Âm là thiếu tiền so với sổ. */
        BigDecimal difference,
        String differenceReason,
        String status
) {
    public static CashShiftResponse from(CashShift c) {
        return new CashShiftResponse(
                c.getId(),
                c.getEmployee().getId(), c.getEmployee().getPerson().getFullName(),
                c.getOpenedAt(), c.getClosedAt(),
                c.getOpeningBalance(), c.getExpectedCash(), c.getCountedCash(),
                c.getDifference(), c.getDifferenceReason(), c.getStatus().name());
    }
}
