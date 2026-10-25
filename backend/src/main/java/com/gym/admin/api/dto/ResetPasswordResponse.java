package com.gym.admin.api.dto;

import com.gym.identity.domain.User;
import com.gym.identity.domain.UserRole;

/**
 * Kết quả đặt lại mật khẩu — áp dụng cho MỌI vai trò (nhân viên lẫn hội viên),
 * không chỉ 4 vai trò nhân sự như các API khác trong gói {@code admin.employees}.
 *
 * <p>{@code tempPassword} CHỈ xuất hiện trong response này — không được lưu lại
 * dạng chữ ở bất kỳ đâu (CSDL chỉ giữ bản băm), không ghi log. Admin phải tự
 * báo lại cho chủ tài khoản ngay lúc này, không xem lại được sau khi rời trang.
 */
public record ResetPasswordResponse(
        Long userId,
        String username,
        String fullName,
        UserRole role,
        String tempPassword
) {
    public static ResetPasswordResponse of(User u, String tempPassword) {
        return new ResetPasswordResponse(
                u.getId(), u.getUsername(), u.getPerson().getFullName(), u.getPrimaryRole(), tempPassword);
    }
}
