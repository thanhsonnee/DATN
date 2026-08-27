package com.gym.membership.api;

import com.gym.membership.api.dto.MembershipResponse;
import com.gym.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Gói tập", description = "Bảng giá công khai")
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
}
