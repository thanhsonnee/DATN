package com.gym.identity.api.dto;

import com.gym.identity.domain.Employee;

/** Nhân sự rút gọn để hiển thị trong danh sách chọn (vd. chọn Sale phụ trách khi tạo Lead). */
public record EmployeeBriefResponse(Long id, String employeeCode, String fullName) {
    public static EmployeeBriefResponse from(Employee e) {
        return new EmployeeBriefResponse(e.getId(), e.getEmployeeCode(), e.getPerson().getFullName());
    }
}
