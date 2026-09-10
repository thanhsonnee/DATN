package com.gym.admin.api.dto;

import com.gym.identity.domain.Department;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.EmployeeStatus;

import java.time.LocalDate;

/** Một dòng trong danh sách nhân viên đã có tài khoản — không có mật khẩu. */
public record EmployeeSummaryResponse(
        Long id,
        String employeeCode,
        String fullName,
        String phone,
        String email,
        Department department,
        String position,
        EmployeeStatus status,
        LocalDate startDate
) {
    public static EmployeeSummaryResponse from(Employee e) {
        return new EmployeeSummaryResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getPerson().getFullName(),
                e.getPerson().getPhone(),
                e.getPerson().getEmail(),
                e.getDepartment(),
                e.getPosition(),
                e.getStatus(),
                e.getStartDate());
    }
}
