package com.gym.billing.api.dto;

import com.gym.billing.domain.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record InvoiceResponse(
        Long id,
        String invoiceNo,
        Long memberId,
        String memberName,
        String memberCode,
        Long registrationId,
        String registrationCode,
        String description,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceDue,
        String status,
        OffsetDateTime issuedAt,
        LocalDate dueDate,
        OffsetDateTime paidAt
) {
    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(
                i.getId(), i.getInvoiceNo(),
                i.getMember().getId(), i.getMember().getPerson().getFullName(),
                i.getMember().getMemberCode(),
                i.getRegistration().getId(), i.getRegistration().getRegistrationCode(),
                i.getDescription(),
                i.getTotalAmount(), i.getPaidAmount(), i.getBalanceDue(),
                i.getStatus().name(), i.getIssuedAt(), i.getDueDate(), i.getPaidAt());
    }
}
