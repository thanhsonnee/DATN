package com.gym.settings.api;

import com.gym.settings.api.dto.SystemSettingResponse;
import com.gym.settings.api.dto.UpdateSystemSettingRequest;
import com.gym.settings.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tham số hệ thống", description = "Cấu hình nghiệp vụ chỉnh được qua API thay vì hardcode")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService service;

    @Operation(summary = "Danh sách toàn bộ tham số hệ thống")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<SystemSettingResponse> danhSach() {
        return service.danhSach();
    }

    @Operation(summary = "Cập nhật giá trị một tham số")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{key}")
    public SystemSettingResponse capNhat(@PathVariable String key,
                                          @Valid @RequestBody UpdateSystemSettingRequest req,
                                          @AuthenticationPrincipal Long userId) {
        return service.capNhat(key, req.value(), userId);
    }
}
