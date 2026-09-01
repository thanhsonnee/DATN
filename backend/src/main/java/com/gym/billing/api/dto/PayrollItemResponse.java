package com.gym.billing.api.dto;

import com.gym.billing.domain.PayrollItem;

import java.math.BigDecimal;

public record PayrollItemResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String department,
        String position,
        BigDecimal baseSalary,
        Integer ptSessionsCount,
        BigDecimal ptCommission,
        Integer salesContractsCount,
        BigDecimal salesCommission,
        BigDecimal bonusAmount,
        BigDecimal deductionAmount,
        BigDecimal netSalary,
        String note
) {
    public static PayrollItemResponse from(PayrollItem item) {
        var emp = item.getEmployee();
        return new PayrollItemResponse(
                item.getId(),
                emp.getId(),
                emp.getEmployeeCode(),
                emp.getPerson().getFullName(),
                emp.getDepartment().name(),
                emp.getPosition(),
                item.getBaseSalary(),
                item.getPtSessionsCount(),
                item.getPtCommission(),
                item.getSalesContractsCount(),
                item.getSalesCommission(),
                item.getBonusAmount(),
                item.getDeductionAmount(),
                item.getNetSalary(),
                item.getNote()
        );
    }
}
