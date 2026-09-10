package com.gym.membership.api.dto;

import com.gym.billing.domain.PaymentMethod;
import com.gym.common.util.ValidationPatterns;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Yêu cầu đăng ký gói tập tại quầy cho khách vãng lai (A1 + A2 Kênh 1).
 */
public record DeskRegistrationRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên khách hàng")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = ValidationPatterns.PHONE_VN, message = "Số điện thoại phải gồm 10 chữ số hợp lệ (VD: 0912345678)")
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 150)
        String email,

        @NotNull(message = "Vui lòng chọn gói tập")
        Long membershipId,

        @PositiveOrZero(message = "Số tiền giảm không được âm")
        BigDecimal discountAmount,

        @Size(max = 255)
        String discountReason,

        Long assignedTrainerId,

        @Size(max = 1000)
        String note,

        boolean payNow,

        PaymentMethod paymentMethod
) {}
