package com.gym.identity.api.dto;

import com.gym.identity.domain.Employee;

import java.math.BigDecimal;

/** Một huấn luyện viên trong danh sách chọn khi đặt lịch — hội viên chọn bằng tên, không phải mã số. */
public record TrainerResponse(
        Long id,
        String employeeCode,
        String fullName,
        String level,
        String specialties,
        String bio,
        BigDecimal ratingAvg,
        Integer ratingCount
) {
    public static TrainerResponse from(Employee e) {
        return new TrainerResponse(
                e.getId(), e.getEmployeeCode(), e.getPerson().getFullName(),
                e.getLevel() == null ? null : e.getLevel().name(),
                e.getSpecialties(), e.getBio(), e.getRatingAvg(), e.getRatingCount());
    }
}
