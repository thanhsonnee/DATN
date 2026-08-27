package com.gym.identity.api.dto;

import jakarta.validation.constraints.*;

/**
 * Dữ liệu đăng ký tài khoản.
 *
 * <p>CỐ TÌNH không có trường {@code role} — vai trò luôn được gán cứng MEMBER
 * trong mã nguồn. Đây là lỗ hổng leo thang đặc quyền kinh điển nếu để client tự khai.
 */
public record RegisterRequest(

        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Pattern(regexp = "^0[35789][0-9]{8}$", message = "Số điện thoại không đúng định dạng Việt Nam")
        String phone,

        @Email(message = "Email không hợp lệ")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 ký tự trở lên")
        String password
) {}
