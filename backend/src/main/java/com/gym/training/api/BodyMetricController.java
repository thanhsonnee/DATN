package com.gym.training.api;

import com.gym.training.api.dto.BodyMetricResponse;
import com.gym.training.api.dto.CreateBodyMetricRequest;
import com.gym.training.service.BodyMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chỉ số cơ thể", description = "Hội viên tự nhập cân nặng/chiều cao, hệ thống tự tính BMI/BMR/WHR")
@RestController
@RequestMapping("/api/v1/body-metrics")
@RequiredArgsConstructor
public class BodyMetricController {

    private final BodyMetricService service;

    @Operation(summary = "Nhập chỉ số mới")
    @PostMapping
    public ResponseEntity<BodyMetricResponse> nhap(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody CreateBodyMetricRequest req) {
        var m = service.nhapChiSo(userId, req.weightKg(), req.heightCm(), req.waistCm(), req.hipCm());
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @Operation(summary = "Lịch sử chỉ số của tôi",
            description = "Sắp xếp tăng dần theo thời gian để vẽ biểu đồ tiến bộ.")
    @GetMapping("/me")
    public List<BodyMetricResponse> cuaToi(@AuthenticationPrincipal Long userId) {
        return service.lichSuCuaToi(userId);
    }

    @Operation(summary = "Lịch sử chỉ số của một hội viên",
            description = "Chỉ PT đang được phân công phụ trách hội viên đó, hoặc Admin, mới xem được.")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @GetMapping("/member/{memberId}")
    public List<BodyMetricResponse> cuaHoiVien(@AuthenticationPrincipal Long userId, @PathVariable Long memberId) {
        return service.lichSuCuaHoiVien(userId, memberId);
    }
}
