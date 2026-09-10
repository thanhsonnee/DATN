package com.gym.feedback.api;

import com.gym.feedback.api.dto.CreateEquipmentRequest;
import com.gym.feedback.api.dto.EquipmentResponse;
import com.gym.feedback.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Thiết bị", description = "Danh mục thiết bị phòng tập — nền cho phản hồi báo hỏng")
@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService service;

    @Operation(summary = "Danh sách thiết bị",
            description = "Mọi vai trò đăng nhập đều xem được — hội viên cần danh sách này để chọn khi báo hỏng.")
    @GetMapping
    public List<EquipmentResponse> danhSach() {
        return service.danhSach();
    }

    @Operation(summary = "Thêm thiết bị mới")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EquipmentResponse> them(@Valid @RequestBody CreateEquipmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.taoThietBi(req));
    }
}
