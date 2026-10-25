package com.gym.training.api;

import com.gym.training.api.dto.CreateWorkoutPlanRequest;
import com.gym.training.api.dto.UpdateWorkoutPlanRequest;
import com.gym.training.api.dto.WorkoutPlanResponse;
import com.gym.training.service.WorkoutPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Giáo án tập luyện", description = "PT soạn giáo án text tự do, hội viên đánh dấu ngày đã tập")
@RestController
@RequestMapping("/api/v1/workout-plans")
@RequiredArgsConstructor
public class WorkoutPlanController {

    private final WorkoutPlanService service;

    @Operation(summary = "Soạn giáo án mới",
            description = "trainerId lấy từ người đang đăng nhập, không nhận từ client.")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @PostMapping
    public ResponseEntity<WorkoutPlanResponse> soan(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody CreateWorkoutPlanRequest req) {
        var p = service.soanGiaoAn(userId, req.registrationId(), req.title(), req.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @Operation(summary = "Sửa giáo án cũ",
            description = "Giữ nguyên lịch sử completedDates đã đánh dấu trước đó. "
                        + "Chỉ PT đã soạn giáo án này (hoặc Admin) sửa được.")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @PutMapping("/{id}")
    public WorkoutPlanResponse sua(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                    @Valid @RequestBody UpdateWorkoutPlanRequest req) {
        return service.suaGiaoAn(userId, id, req.title(), req.content());
    }

    @Operation(summary = "Đánh dấu đã tập hôm nay",
            description = "Bỏ trống ngày thì mặc định hôm nay. Dùng query param (không phải JSON body) vì "
                        + "đây là trường tùy chọn duy nhất — request không gửi body/Content-Type vẫn gọi được.")
    @PostMapping("/{id}/log")
    public WorkoutPlanResponse danhDau(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.danhDauDaTap(userId, id, date);
    }

    @Operation(summary = "Giáo án của tôi",
            description = "Hội viên xem danh sách giáo án PT đã soạn cho mình, mới nhất trước.")
    @GetMapping("/me")
    public List<WorkoutPlanResponse> cuaToi(@AuthenticationPrincipal Long userId) {
        return service.giaoAnCuaToi(userId);
    }
}
