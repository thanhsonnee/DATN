package com.gym.sales.api.dto;

import com.gym.common.util.ValidationPatterns;
import com.gym.sales.domain.LeadSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateLeadRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên khách hàng")
        @Size(max = 150, message = "Họ và tên không quá 150 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = ValidationPatterns.PHONE_VN, message = "Số điện thoại phải gồm 10 chữ số hợp lệ (VD: 0912345678)")
        String phone,

        @Size(max = 150, message = "Email không quá 150 ký tự")
        String email,

        @NotNull(message = "Vui lòng chọn nguồn tiếp nhận")
        LeadSource source,

        Long interestedMembershipId,

        Long assignedToEmployeeId,

        @Size(max = 500, message = "Ghi chú không quá 500 ký tự")
        String note,

        LocalDate nextFollowUp
) {}
