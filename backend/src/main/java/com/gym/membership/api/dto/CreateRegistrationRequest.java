package com.gym.membership.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Yêu cầu chốt mua gói.
 *
 * <p>{@code personId} chỉ dùng khi nhân viên tạo hợp đồng hộ khách tại quầy.
 * Hội viên tự mua trên app thì bỏ trống — hệ thống lấy chính tài khoản đang đăng nhập,
 * để không ai mua gói dưới tên người khác.
 */
public record CreateRegistrationRequest(

        @NotNull(message = "Vui lòng chọn gói tập")
        Long membershipId,

        Long personId,

        /** Số tiền giảm theo chương trình khuyến mãi đang công khai. */
        @PositiveOrZero(message = "Số tiền giảm không được âm")
        BigDecimal discountAmount,

        /** Tên chương trình khuyến mãi. Bắt buộc khi có giảm giá. */
        @Size(max = 255)
        String discountReason,

        Long assignedTrainerId,

        @Size(max = 1000)
        String note,

        /** Hợp đồng đang gia hạn tiếp nối — bỏ trống nếu đây là gói hoàn toàn mới, không nối vào đâu cả. */
        Long renewFromRegistrationId
) {}
