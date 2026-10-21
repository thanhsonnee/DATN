package com.gym.billing.api.dto;

import com.gym.billing.domain.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Một dòng thu/hoàn tiền trong báo cáo Đối soát doanh thu theo ngày (E6). */
public record PaymentLineResponse(
        Long id,
        String paymentNo,
        String paymentType,
        String method,
        /** Dương là thu vào, âm là hoàn trả. */
        BigDecimal amount,
        String memberName,
        String registrationCode,
        String invoiceNo,
        String receivedByName,
        OffsetDateTime paidAt,
        boolean daXacNhan
) {
    public static PaymentLineResponse from(Payment p) {
        var inv = p.getInvoice();
        return new PaymentLineResponse(
                p.getId(), p.getPaymentNo(), p.getPaymentType().name(), p.getMethod().name(), p.getAmount(),
                p.getMember().getPerson().getFullName(),
                inv.getRegistration().getRegistrationCode(),
                inv.getInvoiceNo(),
                p.getReceivedBy() != null ? p.getReceivedBy().getPerson().getFullName() : null,
                p.getPaidAt(),
                p.getReconciledAt() != null);
    }
}
