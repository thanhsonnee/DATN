package com.gym.admin.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Admin đặt lại mật khẩu cho một tài khoản bất kỳ, tìm theo số điện thoại (= username). */
public record ResetPasswordRequest(
        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String phone
) {}
