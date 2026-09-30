package com.gym.membership.api;

import com.gym.membership.api.dto.CreateMembershipRequest;
import com.gym.membership.api.dto.MembershipAdminResponse;
import com.gym.membership.api.dto.MembershipResponse;
import com.gym.membership.api.dto.UpdateMembershipRequest;
import com.gym.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Gói tập", description = "Bảng giá công khai + quản lý gói tập (Admin)")
@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @Operation(summary = "Bảng giá công khai",
            description = "Không cần đăng nhập. Khách xem giá và chương trình khuyến mãi "
                        + "trước khi quyết định, nên hệ thống không có bước báo giá riêng.")
    @GetMapping
    public List<MembershipResponse> list() {
        return membershipService.listOnSale();
    }

    @Operation(summary = "[CHƯA DÙNG] Chi tiết một gói tập",
            description = "Hiện chưa có nơi nào trong frontend gọi API này — trang Bảng giá "
                        + "hiện toàn bộ gói cùng lúc từ GET /memberships, chưa có màn hình "
                        + "xem chi tiết riêng từng gói.")
    @GetMapping("/{id}")
    public MembershipResponse getById(@PathVariable Long id) {
        return membershipService.getById(id);
    }

    @Operation(summary = "Danh sách gói tập cho Admin quản lý",
            description = "Khác GET công khai ở chỗ trả về CẢ gói đã ngừng bán (ARCHIVED).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public List<MembershipAdminResponse> listForAdmin() {
        return membershipService.listAllForAdmin();
    }

    @Operation(summary = "Thêm gói tập mới")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<MembershipAdminResponse> create(@Valid @RequestBody CreateMembershipRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.create(req));
    }

    @Operation(summary = "Sửa gói tập",
            description = "Sửa cả giá, mô tả lẫn trạng thái bán (ACTIVE/ARCHIVED).")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public MembershipAdminResponse update(@PathVariable Long id, @Valid @RequestBody UpdateMembershipRequest req) {
        return membershipService.update(id, req);
    }

    @Operation(summary = "Xóa gói tập",
            description = "Xóa mềm — hợp đồng cũ đã dùng gói này không bị ảnh hưởng.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        membershipService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
