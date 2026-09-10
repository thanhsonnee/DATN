package com.gym.admin.api;

import com.gym.admin.api.dto.AdminDashboardResponse;
import com.gym.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Quản trị — Dashboard",
        description = "Tổng quan Hội viên & Doanh thu, Vận hành hôm nay cho Admin")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService service;

    @Operation(summary = "Tổng quan dashboard Admin")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public AdminDashboardResponse tongQuan() {
        return service.tongQuan();
    }
}
