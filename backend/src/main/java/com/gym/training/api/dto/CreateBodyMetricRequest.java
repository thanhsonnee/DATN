package com.gym.training.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Yêu cầu nhập chỉ số cơ thể mới. BMI/BMR/WHR do service tự tính, không nhận từ client. */
public record CreateBodyMetricRequest(

        @NotNull(message = "Vui lòng nhập cân nặng")
        @DecimalMin(value = "20", message = "Cân nặng không hợp lệ")
        @DecimalMax(value = "300", message = "Cân nặng không hợp lệ")
        BigDecimal weightKg,

        @NotNull(message = "Vui lòng nhập chiều cao")
        @DecimalMin(value = "80", message = "Chiều cao không hợp lệ")
        @DecimalMax(value = "250", message = "Chiều cao không hợp lệ")
        BigDecimal heightCm,

        BigDecimal waistCm,
        BigDecimal hipCm
) {}
