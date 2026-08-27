package com.gym.training.api;

import com.gym.common.exception.ApiException;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.training.api.dto.SoCaiResponse;
import com.gym.training.service.SessionCreditLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Sổ cái buổi tập",
     description = "Số buổi còn lại và toàn bộ lịch sử biến động")
@RestController
@RequestMapping("/api/v1/registrations/{registrationId}/credit-ledger")
@RequiredArgsConstructor
public class SoCaiController {

    private final SessionCreditLedgerService soCai;
    private final RegistrationRepository registrationRepo;

    @Operation(summary = "Xem sổ cái buổi tập",
            description = "Trả về số dư hiện tại kèm TOÀN BỘ lịch sử, để hội viên không chỉ "
                        + "biết còn mấy buổi mà còn giải thích được vì sao còn ngần ấy.")
    @GetMapping
    public SoCaiResponse xemSoCai(@PathVariable Long registrationId) {
        return SoCaiResponse.of(
                registrationId,
                soCai.soDuHienTai(registrationId),
                soCai.batBienConDung(registrationId),
                soCai.lichSu(registrationId));
    }

    @Operation(summary = "[CHƯA DÙNG] Điều chỉnh thủ công số buổi",
            description = "Loại bút toán DUY NHẤT do con người quyết định, nên bắt buộc ghi "
                        + "lý do và lưu lại người thực hiện. Không sửa bút toán cũ — ghi thêm "
                        + "bút toán mới, vết cũ giữ nguyên. Hiện chưa có màn hình admin nào gọi "
                        + "API này — muốn điều chỉnh phải test thủ công qua Swagger/curl.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/adjust")
    public SoCaiResponse dieuChinh(@PathVariable Long registrationId,
                                   @AuthenticationPrincipal Long userId,
                                   @RequestBody DieuChinhRequest req) {

        var hopDong = registrationRepo.findById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng"));

        soCai.dieuChinh(hopDong, req.delta(), req.reason(), userId);

        return SoCaiResponse.of(registrationId, soCai.soDuHienTai(registrationId),
                soCai.batBienConDung(registrationId), soCai.lichSu(registrationId));
    }

    public record DieuChinhRequest(
            int delta,
            @NotBlank(message = "Điều chỉnh thủ công bắt buộc ghi lý do")
            @Size(max = 255) String reason) {}
}
