package com.gym.billing.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdatePayrollItemRequest(
        @PositiveOrZero(message = "Tiền thưởng không được âm")
        BigDecimal bonusAmount,

        @PositiveOrZero(message = "Tiền khấu trừ không được âm")
        BigDecimal deductionAmount,

        @Size(max = 500, message = "Ghi chú quá dài")
        String note
) {}
