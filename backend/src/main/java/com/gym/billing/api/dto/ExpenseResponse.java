package com.gym.billing.api.dto;

import com.gym.billing.domain.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExpenseResponse(
        Long id,
        String expenseNo,
        String category,
        String title,
        BigDecimal amount,
        LocalDate spentAt,
        String spentByName,
        String approvedByName,
        String status,
        String paymentMethod,
        String receiptUrl,
        String note,
        OffsetDateTime createdAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getExpenseNo(),
                e.getCategory().name(),
                e.getTitle(),
                e.getAmount(),
                e.getSpentAt(),
                e.getSpentBy() != null ? e.getSpentBy().getPerson().getFullName() : null,
                e.getApprovedBy() != null ? e.getApprovedBy().getPerson().getFullName() : null,
                e.getStatus().name(),
                e.getPaymentMethod().name(),
                e.getReceiptUrl(),
                e.getNote(),
                e.getCreatedAt()
        );
    }
}
