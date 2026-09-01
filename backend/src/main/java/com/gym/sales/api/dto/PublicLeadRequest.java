package com.gym.sales.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublicLeadRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên của bạn")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại để nhận tư vấn")
        @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại phải gồm 10 chữ số hợp lệ (VD: 0912345678)")
        String phone,

        @Size(max = 150)
        String email,

        Long interestedMembershipId,

        @Size(max = 500)
        String note
) {}
