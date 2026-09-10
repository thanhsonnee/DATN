package com.gym.billing.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ProfitLossReportResponse(
        Integer month,
        Integer year,
        BigDecimal cashRevenue,
        BigDecimal accrualRevenue,
        BigDecimal operatingExpenses,
        BigDecimal salaryExpenses,
        BigDecimal totalExpenses,
        BigDecimal netProfitCashBasis,
        BigDecimal netProfitAccrualBasis,
        Map<String, BigDecimal> expensesByCategory
) {}
