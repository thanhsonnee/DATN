package com.gym.admin.api;

import com.gym.admin.api.dto.CreateEmployeeAccountRequest;
import com.gym.admin.api.dto.EmployeeAccountResponse;
import com.gym.admin.api.dto.EmployeeSummaryResponse;
import com.gym.admin.service.AdminEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Quản trị — Nhân sự",
        description = "Admin tạo tài khoản cho nhân viên (PT/Sale/Lễ tân/Kế toán) — các vai trò này không tự đăng ký được")
@RestController
@RequestMapping("/api/v1/admin/employees")
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final AdminEmployeeService service;

    @Operation(summary = "Tạo tài khoản nhân viên mới",
            description = "Sinh mật khẩu tạm ngẫu nhiên, trả về ĐÚNG MỘT LẦN trong response này — "
                        + "không lưu lại dạng chữ ở đâu cả, không gửi email. Admin phải tự báo lại cho nhân viên.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EmployeeAccountResponse> taoTaiKhoan(
            @Valid @RequestBody CreateEmployeeAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.taoTaiKhoanNhanVien(req));
    }

    @Operation(summary = "Danh sách nhân viên đã có tài khoản")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<EmployeeSummaryResponse> danhSach() {
        return service.danhSachNhanVien();
    }
}
