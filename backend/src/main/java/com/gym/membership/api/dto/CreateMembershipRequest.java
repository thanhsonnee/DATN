package com.gym.membership.api.dto;

import com.gym.membership.domain.PackageType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateMembershipRequest(
        @NotBlank(message = "Vui lòng nhập mã gói")
        @Size(max = 30, message = "Mã gói không quá 30 ký tự")
        String code,

        @NotBlank(message = "Vui lòng nhập tên gói")
        @Size(max = 150, message = "Tên gói không quá 150 ký tự")
        String name,

        @NotNull(message = "Vui lòng chọn loại gói")
        PackageType packageType,

        Integer durationDays,

        Integer sessionCount,

        @NotNull(message = "Vui lòng nhập giá")
        @DecimalMin(value = "0", message = "Giá không được âm")
        BigDecimal price,

        boolean includesTrainer,

        BigDecimal ptValueRatio,

        @Min(value = 0, message = "Số ngày bảo lưu không được âm")
        int maxFreezeDays,

        boolean isRefundable,

        @Size(max = 5000, message = "Mô tả không quá 5000 ký tự")
        String description,

        int displayOrder
) {}
