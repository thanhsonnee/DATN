package com.gym.sales.api.dto;

import jakarta.validation.constraints.NotNull;

public record AssignLeadRequest(
        @NotNull(message = "Vui lòng chọn nhân viên phụ trách")
        Long employeeId
) {}
