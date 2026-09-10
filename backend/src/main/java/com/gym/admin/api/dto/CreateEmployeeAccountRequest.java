package com.gym.admin.api.dto;

import com.gym.identity.domain.EmploymentType;
import com.gym.identity.domain.UserRole;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dữ liệu Admin nhập để tạo tài khoản cho nhân viên (PT/Sale/Lễ tân/Kế toán).
 *
 * <p>{@code role} bắt buộc nhập ở đây (khác với {@code RegisterRequest} tự đăng
 * ký, luôn gán cứng MEMBER) — nhưng vẫn bị {@code AdminEmployeeService} kiểm
 * tra chỉ chấp nhận 4 vai trò nhân sự, không cho tạo ADMIN/MEMBER qua đường này.
 */
public record CreateEmployeeAccountRequest(

        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "^0[35789][0-9]{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
        String phone,

        @Email(message = "Email không hợp lệ")
        @Size(max = 150)
        String email,

        @NotNull(message = "Vui lòng chọn vai trò")
        UserRole role,

        @Size(max = 80)
        String position,

        EmploymentType employmentType,

        @DecimalMin(value = "0", message = "Lương cứng không được âm")
        BigDecimal baseSalary,

        /** Bỏ trống = hôm nay. */
        LocalDate startDate
) {}
