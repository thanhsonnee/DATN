package com.gym.billing.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CalculatePayrollRequest(
        @NotNull(message = "Vui lòng chọn tháng")
        @Min(value = 1, message = "Tháng phải từ 1 đến 12")
        @Max(value = 12, message = "Tháng phải từ 1 đến 12")
        Integer periodMonth,

        @NotNull(message = "Vui lòng chọn năm")
        @Min(value = 2020, message = "Năm không hợp lệ")
        Integer periodYear
) {}
