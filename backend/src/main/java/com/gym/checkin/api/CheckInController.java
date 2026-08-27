package com.gym.checkin.api;

import com.gym.checkin.api.dto.CheckInPreviewResponse;
import com.gym.checkin.api.dto.CheckInResponse;
import com.gym.checkin.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Check-in", description = "Kiểm soát ra vào phòng tập")
@RestController
@RequestMapping("/api/v1/check-ins")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "Xem trước tình trạng hội viên",
            description = "KHÔNG ghi lượt check-in nào. Dùng ngay sau khi lễ tân chọn hội viên "
                        + "từ ô tìm kiếm, để hiện tên/mã/hạn gói/có vào được không TRƯỚC khi bấm "
                        + "xác nhận — thay vì bấm mù rồi mới biết kết quả.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @GetMapping("/preview/{memberId}")
    public CheckInPreviewResponse xemTruoc(@PathVariable Long memberId) {
        return checkInService.xemTruoc(memberId);
    }

    @Operation(summary = "Lễ tân quét mã cho hội viên vào",
            description = "Trả về ảnh hồ sơ và thông tin gói để lễ tân ĐỐI CHIẾU người thật. "
                        + "Máy không tự mở cửa — người vẫn là chốt chặn cuối. "
                        + "Lượt bị từ chối cũng được ghi lại để thống kê.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @PostMapping
    public CheckInResponse quetVao(@AuthenticationPrincipal Long userId,
                                   @Valid @RequestBody QuetVaoRequest req) {
        return checkInService.quetVao(req.memberId(), userId, Boolean.TRUE.equals(req.override()));
    }

    @Operation(summary = "Quét ra khi về")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @PostMapping("/{memberId}/check-out")
    public CheckInResponse quetRa(@PathVariable Long memberId) {
        return checkInService.quetRa(memberId);
    }

    @Operation(summary = "Ai đang ở trong phòng tập")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','TRAINER','ADMIN')")
    @GetMapping("/inside")
    public List<CheckInResponse> dangTrongPhong() {
        return checkInService.dangTrongPhongTap();
    }

    @Operation(summary = "[CHƯA DÙNG] Lịch sử ra vào của một hội viên",
            description = "Có hook sẵn ở frontend (useLichSuCheckIn) nhưng chưa trang nào gọi "
                        + "tới — chưa có màn hình xem lịch sử ra vào theo từng hội viên.")
    @GetMapping("/member/{memberId}")
    public List<CheckInResponse> lichSu(@PathVariable Long memberId) {
        return checkInService.lichSuCuaHoiVien(memberId);
    }

    @Operation(summary = "Thống kê hiệu quả chống thất thoát",
            description = "Đếm lượt vào theo từng kết quả. Số lượt BỊ CHẶN mới là chỉ số "
                        + "đo hiệu quả của cơ chế kiểm soát.")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @GetMapping("/stats")
    public Map<String, Long> thongKe(@RequestParam(defaultValue = "30") int days) {
        Map<String, Long> ketQua = new LinkedHashMap<>();
        for (Object[] dong : checkInService.thongKe(days)) {
            ketQua.put(String.valueOf(dong[0]), ((Number) dong[1]).longValue());
        }
        return ketQua;
    }

    public record QuetVaoRequest(
            @NotNull(message = "Thiếu mã hội viên") Long memberId,
            /** Lễ tân chủ động bỏ qua cảnh báo nợ tiền và cho vào. */
            Boolean override) {}
}
