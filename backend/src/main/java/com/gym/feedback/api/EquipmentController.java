package com.gym.feedback.api;

import com.gym.feedback.api.dto.CreateEquipmentRequest;
import com.gym.feedback.api.dto.EquipmentResponse;
import com.gym.feedback.api.dto.UpdateEquipmentRequest;
import com.gym.feedback.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Sửa thiết bị", description = "Sửa tên/khu vực/ghi chú, cho phép đổi trạng thái tay.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public EquipmentResponse sua(@PathVariable Long id, @Valid @RequestBody UpdateEquipmentRequest req) {
        return service.suaThietBi(id, req);
    }

    @Operation(summary = "Xóa thiết bị", description = "Dùng khi thanh lý hoặc thêm nhầm. Xóa mềm.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> xoa(@PathVariable Long id) {
        service.xoaThietBi(id);
        return ResponseEntity.noContent().build();
    }
}
