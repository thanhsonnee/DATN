package com.gym.admin.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Sửa nhân viên — CHỈ họ tên và lương cơ bản. Không có SĐT (SĐT = username đăng
 * nhập, đổi cần đồng bộ riêng — chưa làm) và không có vai trò (đổi vai trò kéo
 * theo đổi cả department/level, rủi ro cao, cần luồng riêng nếu sau này cần).
 * Cả hai field đều optional — field nào null thì giữ nguyên giá trị cũ.
 */
public record UpdateEmployeeRequest(
        @Size(max = 150, message = "Họ tên không quá 150 ký tự")
        String fullName,

        @DecimalMin(value = "0", message = "Lương cơ bản không được âm")
        BigDecimal baseSalary
) {}
