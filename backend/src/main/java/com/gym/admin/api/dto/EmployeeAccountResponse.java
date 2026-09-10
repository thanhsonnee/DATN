package com.gym.admin.api.dto;

import com.gym.identity.domain.Department;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.User;
import com.gym.identity.domain.UserRole;

/**
 * Kết quả tạo tài khoản nhân viên.
 *
 * <p>{@code tempPassword} CHỈ xuất hiện trong response này — không được lưu
 * lại dạng chữ ở bất kỳ đâu (CSDL chỉ giữ bản băm), không ghi log. Admin phải
 * tự báo lại cho nhân viên ngay lúc này, không xem lại được sau khi rời trang.
 */
public record EmployeeAccountResponse(
        Long employeeId,
        Long userId,
        String employeeCode,
        String username,
        String fullName,
        UserRole role,
        Department department,
        String tempPassword
) {
    public static EmployeeAccountResponse of(Employee e, User u, String tempPassword) {
        return new EmployeeAccountResponse(
                e.getId(),
                u.getId(),
                e.getEmployeeCode(),
                u.getUsername(),
                e.getPerson().getFullName(),
                u.getPrimaryRole(),
                e.getDepartment(),
                tempPassword);
    }
}
