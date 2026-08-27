package com.gym.membership.api;

import com.gym.membership.api.dto.*;
import com.gym.membership.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Hợp đồng", description = "Chốt mua gói, kích hoạt, bảo lưu")
@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @Operation(summary = "Chốt mua gói",
            description = "Tạo hồ sơ hội viên nếu đây là lần mua đầu tiên, rồi tạo hợp đồng "
                        + "ở trạng thái chờ thanh toán.")
    @PostMapping
    public ResponseEntity<RegistrationResponse> create(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody CreateRegistrationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.create(userId, req));
    }

    @Operation(summary = "Hợp đồng của tôi")
    @GetMapping("/me")
    public List<RegistrationResponse> myRegistrations(@AuthenticationPrincipal Long userId) {
        return registrationService.myRegistrations(userId);
    }

    @Operation(summary = "[CHƯA DÙNG] Chi tiết hợp đồng",
            description = "Hiện chưa có nơi nào trong frontend gọi API này — mọi màn hình đều "
                        + "dùng sẵn danh sách trả về từ các API khác (/me, /pending-payment...).")
    @GetMapping("/{id}")
    public RegistrationResponse getById(@PathVariable Long id) {
        return registrationService.getById(id);
    }

    @Operation(summary = "[CHƯA DÙNG] Kích hoạt hợp đồng sau khi thu đủ tiền",
            description = "ĐÃ ĐƯỢC THAY THẾ: module thanh toán giờ tự gọi bước này bên trong "
                        + "khi thu đủ tiền (xem POST /billing/registrations/{id}/confirm), nên "
                        + "endpoint này không còn được frontend gọi trực tiếp nữa. Giữ lại vì "
                        + "backend nội bộ vẫn có thể cần kích hoạt thủ công qua Swagger.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','SALE','ADMIN','ACCOUNTANT')")
    @PostMapping("/{id}/activate")
    public RegistrationResponse activate(@PathVariable Long id) {
        return registrationService.activate(id);
    }

    @Operation(summary = "Xin bảo lưu gói tập",
            description = "Mỗi hợp đồng chỉ được bảo lưu MỘT LẦN.")
    @PostMapping("/{id}/freeze")
    public RegistrationResponse requestFreeze(@PathVariable Long id,
                                              @AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody FreezeRequest req) {
        return registrationService.requestFreeze(id, userId, req);
    }

    @Operation(summary = "Duyệt hoặc từ chối yêu cầu bảo lưu",
            description = "Khi duyệt, ngày hết hạn hợp đồng được đẩy lùi đúng số ngày bảo lưu.")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @PostMapping("/{id}/freeze/decision")
    public RegistrationResponse decideFreeze(@PathVariable Long id,
                                             @AuthenticationPrincipal Long userId,
                                             @RequestBody FreezeDecision decision) {
        return registrationService.approveFreeze(id, userId, decision.approved(), decision.reason());
    }

    @Operation(summary = "Danh sách hợp đồng chờ thanh toán",
            description = "Hợp đồng hội viên đã chốt mua nhưng chưa trả tiền. Lễ tân dùng "
                        + "danh sách này để biết cần xuất hóa đơn cho ai, thay vì phải hỏi "
                        + "hội viên đọc mã số hợp đồng.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','SALE','ACCOUNTANT','ADMIN')")
    @GetMapping("/pending-payment")
    public List<RegistrationResponse> choThanhToan() {
        return registrationService.choThanhToan();
    }

    @Operation(summary = "Danh sách yêu cầu bảo lưu đang chờ duyệt",
            description = "Nhân viên thấy và duyệt trực tiếp, không cần biết trước mã số hợp đồng.")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @GetMapping("/pending-freeze")
    public List<RegistrationResponse> choDuyetBaoLuu() {
        return registrationService.choDuyetBaoLuu();
    }

    @Operation(summary = "Danh sách hợp đồng sắp hết hạn",
            description = "Dữ liệu để Sale gọi mời gia hạn.")
    @PreAuthorize("hasAnyRole('SALE','ADMIN','RECEPTIONIST')")
    @GetMapping("/expiring")
    public List<RegistrationResponse> expiring(@RequestParam(defaultValue = "14") int days) {
        return registrationService.expiringWithin(days);
    }

    public record FreezeDecision(boolean approved, @Size(max = 255) String reason) {}
}
