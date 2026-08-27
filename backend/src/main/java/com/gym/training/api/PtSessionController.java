package com.gym.training.api;

import com.gym.training.api.dto.DatLichRequest;
import com.gym.training.api.dto.PtSessionResponse;
import com.gym.training.domain.ActorSide;
import com.gym.training.domain.SessionType;
import com.gym.training.service.PtSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Buổi tập PT", description = "Đặt lịch, xác nhận hai chiều, hủy buổi")
@RestController
@RequestMapping("/api/v1/pt-sessions")
@RequiredArgsConstructor
public class PtSessionController {

    private final PtSessionService service;

    @Operation(summary = "Đặt lịch tập",
            description = "Buổi ở trạng thái chờ huấn luyện viên duyệt. CHƯA trừ buổi — "
                        + "chỉ trừ khi buổi thực sự hoàn thành.")
    @PostMapping
    public ResponseEntity<PtSessionResponse> datLich(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody DatLichRequest req) {
        var s = service.datLich(userId, req.registrationId(), req.trainerId(),
                req.scheduledStart(), req.scheduledEnd(),
                req.sessionType() == null ? null : SessionType.valueOf(req.sessionType()),
                req.roomName());
        return ResponseEntity.status(HttpStatus.CREATED).body(s);
    }

    @Operation(summary = "Huấn luyện viên duyệt lịch")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @PostMapping("/{id}/approve")
    public PtSessionResponse duyet(@PathVariable Long id) {
        return service.duyetLich(id);
    }

    @Operation(summary = "Huấn luyện viên từ chối lịch")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @PostMapping("/{id}/reject")
    public PtSessionResponse tuChoi(@PathVariable Long id, @Valid @RequestBody LyDoRequest req) {
        return service.tuChoiLich(id, req.reason());
    }

    @Operation(summary = "Huấn luyện viên xác nhận đã dạy xong",
            description = "Bước MỘT của xác nhận hai chiều. Đây là điều kiện khởi động: "
                        + "không bấm thì buổi không bao giờ được tính công.")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @PostMapping("/{id}/trainer-confirm")
    public PtSessionResponse huanLuyenVienXacNhan(@PathVariable Long id,
                                                  @AuthenticationPrincipal Long userId) {
        return service.huanLuyenVienXacNhan(id, userId);
    }

    @Operation(summary = "Hội viên xác nhận đã tập",
            description = "Bước HAI của xác nhận hai chiều. Đủ cả hai thì buổi hoàn thành "
                        + "và sổ cái trừ đi một buổi.")
    @PostMapping("/{id}/member-confirm")
    public PtSessionResponse hoiVienXacNhan(@PathVariable Long id,
                                            @AuthenticationPrincipal Long userId) {
        return service.hoiVienXacNhan(id, userId);
    }

    @Operation(summary = "Hủy buổi tập",
            description = "Hủy sớm thì buổi được hoàn lại sổ cái. Hủy sát giờ thì mất buổi.")
    @PostMapping("/{id}/cancel")
    public PtSessionResponse huy(@PathVariable Long id, @Valid @RequestBody HuyRequest req) {
        return service.huyBuoi(id, ActorSide.valueOf(req.cancelledBy()), req.reason());
    }

    @Operation(summary = "Đánh dấu vắng mặt",
            description = "Hội viên vắng thì mất buổi. Huấn luyện viên vắng thì hội viên "
                        + "không mất gì.")
    @PreAuthorize("hasAnyRole('TRAINER','RECEPTIONIST','ADMIN')")
    @PostMapping("/{id}/no-show")
    public PtSessionResponse vangMat(@PathVariable Long id, @RequestBody VangMatRequest req) {
        return service.danhDauVangMat(id, ActorSide.valueOf(req.noShowBy()), req.note());
    }

    @Operation(summary = "Buổi tập của tôi")
    @GetMapping("/me")
    public List<PtSessionResponse> cuaToi(@AuthenticationPrincipal Long userId) {
        return service.buoiTapCuaHoiVien(userId);
    }

    @Operation(summary = "Lịch dạy của tôi")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @GetMapping("/trainer/me")
    public List<PtSessionResponse> lichDay(@AuthenticationPrincipal Long userId) {
        return service.lichDayCuaHuanLuyenVien(userId);
    }

    @Operation(summary = "[CHƯA DÙNG] Chi tiết buổi tập",
            description = "Hiện chưa có nơi nào trong frontend gọi API này — trang buổi tập "
                        + "dùng danh sách trả về sẵn từ /pt-sessions/me, không cần tra riêng.")
    @GetMapping("/{id}")
    public PtSessionResponse chiTiet(@PathVariable Long id) {
        return service.chiTiet(id);
    }

    public record LyDoRequest(@NotBlank(message = "Vui lòng nhập lý do") @Size(max = 255) String reason) {}

    public record HuyRequest(
            @jakarta.validation.constraints.Pattern(regexp = "MEMBER|TRAINER|SYSTEM")
            String cancelledBy,
            @Size(max = 255) String reason) {}

    public record VangMatRequest(
            @jakarta.validation.constraints.Pattern(regexp = "MEMBER|TRAINER")
            String noShowBy,
            @Size(max = 255) String note) {}
}
