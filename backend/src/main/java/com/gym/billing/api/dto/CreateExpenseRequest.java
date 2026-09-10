package com.gym.billing.api.dto;

import com.gym.billing.domain.ExpenseCategory;
import com.gym.billing.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(
        @NotNull(message = "Vui lòng chọn danh mục chi phí")
        ExpenseCategory category,

        @NotBlank(message = "Tiêu đề khoản chi không được để trống")
        @Size(max = 255, message = "Tiêu đề không quá 255 ký tự")
        String title,

        @NotNull(message = "Số tiền chi không được để trống")
        @Positive(message = "Số tiền chi phải lớn hơn 0")
        BigDecimal amount,

        @NotNull(message = "Vui lòng chọn ngày chi")
        LocalDate spentAt,

        @NotNull(message = "Vui lòng chọn hình thức thanh toán")
        PaymentMethod paymentMethod,

        @Size(max = 500, message = "Link chứng từ không quá 500 ký tự")
        String receiptUrl,

        String note
) {}
