package com.gym.billing.api.dto;

import com.gym.billing.domain.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        String paymentNo,
        Long invoiceId,
        String paymentType,
        String method,
        /** Dương là thu vào, âm là hoàn trả. */
        BigDecimal amount,
        String status,
        Long cashShiftId,
        String refundReason,
        OffsetDateTime paidAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getPaymentNo(), p.getInvoice().getId(),
                p.getPaymentType().name(), p.getMethod().name(), p.getAmount(),
                p.getStatus().name(),
                p.getCashShift() == null ? null : p.getCashShift().getId(),
                p.getRefundReason(), p.getPaidAt());
    }
}
