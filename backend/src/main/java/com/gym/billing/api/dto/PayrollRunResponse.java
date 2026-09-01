package com.gym.billing.api.dto;

import com.gym.billing.domain.PayrollRun;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PayrollRunResponse(
        Long id,
        String payrollCode,
        Integer periodMonth,
        Integer periodYear,
        BigDecimal totalBaseSalary,
        BigDecimal totalCommission,
        BigDecimal totalBonus,
        BigDecimal totalDeduction,
        BigDecimal totalNetSalary,
        String status,
        String createdByName,
        String approvedByName,
        OffsetDateTime approvedAt,
        OffsetDateTime paidAt,
        String note,
        List<PayrollItemResponse> items
) {
    public static PayrollRunResponse from(PayrollRun pr) {
        return new PayrollRunResponse(
                pr.getId(),
                pr.getPayrollCode(),
                pr.getPeriodMonth(),
                pr.getPeriodYear(),
                pr.getTotalBaseSalary(),
                pr.getTotalCommission(),
                pr.getTotalBonus(),
                pr.getTotalDeduction(),
                pr.getTotalNetSalary(),
                pr.getStatus().name(),
                pr.getCreatedBy() != null ? pr.getCreatedBy().getPerson().getFullName() : null,
                pr.getApprovedBy() != null ? pr.getApprovedBy().getPerson().getFullName() : null,
                pr.getApprovedAt(),
                pr.getPaidAt(),
                pr.getNote(),
                pr.getItems() != null ? pr.getItems().stream().map(PayrollItemResponse::from).toList() : List.of()
        );
    }
}
