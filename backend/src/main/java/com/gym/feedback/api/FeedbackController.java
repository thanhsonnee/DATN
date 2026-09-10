package com.gym.feedback.api;

import com.gym.feedback.api.dto.CreateFeedbackRequest;
import com.gym.feedback.api.dto.FeedbackResponse;
import com.gym.feedback.api.dto.UpdateFeedbackRequest;
import com.gym.feedback.api.dto.UpdateFeedbackStatusRequest;
import com.gym.feedback.domain.FeedbackStatus;
import com.gym.feedback.domain.FeedbackType;
import com.gym.feedback.service.FeedbackService;
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

@Tag(name = "Phản hồi & sự cố thiết bị",
     description = "Đánh giá PT, báo hỏng thiết bị, góp ý — định tuyến tự động theo loại")
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    @Operation(summary = "Gửi phản hồi",
            description = "TRAINER cần trainerId (+ rating nếu muốn chấm điểm), "
                        + "FACILITY cần equipmentId. Gửi xong hệ thống xử lý ngay: "
                        + "đổi trạng thái thiết bị hoặc cộng dồn điểm PT.")
    @PostMapping
    public ResponseEntity<FeedbackResponse> guiPhanHoi(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody CreateFeedbackRequest req) {
        var res = service.guiPhanHoi(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Phản hồi của tôi", description = "Hội viên xem lại lịch sử phản hồi đã gửi và trạng thái xử lý.")
    @GetMapping("/me")
    public List<FeedbackResponse> phanHoiCuaToi(@AuthenticationPrincipal Long userId) {
        return service.phanHoiCuaToi(userId);
    }

    @Operation(summary = "Sửa phản hồi của tôi",
            description = "Chỉ sửa được nội dung và (với loại TRAINER) đánh giá lại số sao — "
                        + "không đổi được loại phản hồi hay đối tượng (HLV/thiết bị).")
    @PutMapping("/{id}")
    public FeedbackResponse suaPhanHoi(@PathVariable Long id,
                                       @AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody UpdateFeedbackRequest req) {
        return service.suaPhanHoi(id, userId, req);
    }

    @Operation(summary = "Đánh giá về tôi", description = "Huấn luyện viên xem lại các đánh giá hội viên gửi về mình.")
    @PreAuthorize("hasAnyRole('TRAINER','ADMIN')")
    @GetMapping("/trainer/me")
    public List<FeedbackResponse> phanHoiVeToi(@AuthenticationPrincipal Long userId) {
        return service.phanHoiVeToi(userId);
    }

    @Operation(summary = "Hàng đợi xử lý phản hồi", description = "Lọc theo trạng thái/loại — mặc định trả toàn bộ.")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @GetMapping
    public List<FeedbackResponse> hangDoi(@RequestParam(required = false) FeedbackStatus status,
                                          @RequestParam(required = false) FeedbackType type) {
        return service.hangDoi(status, type);
    }

    @Operation(summary = "Cập nhật trạng thái xử lý",
            description = "OPEN → IN_PROGRESS → (WAITING_PARTS) → RESOLVED → CLOSED. "
                        + "Đóng phản hồi FACILITY kèm repairCost > 0 sẽ tự sinh 1 dòng chi phí bảo trì.")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    @PutMapping("/{id}/status")
    public FeedbackResponse capNhatTrangThai(@PathVariable Long id,
                                             @AuthenticationPrincipal Long userId,
                                             @Valid @RequestBody UpdateFeedbackStatusRequest req) {
        return service.capNhatTrangThai(id, userId, req.status(), req.resolutionNote(), req.repairCost());
    }
}
