package com.gym.billing.api;

import com.gym.billing.api.dto.CashShiftResponse;
import com.gym.billing.api.dto.InvoiceResponse;
import com.gym.billing.api.dto.PaymentResponse;
import com.gym.billing.domain.PaymentMethod;
import com.gym.billing.service.BillingService;
import com.gym.membership.api.dto.RegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Thanh toán", description = "Hóa đơn, thu tiền, ca làm việc")
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billing;

    // ---------------------------------------------------------------- hóa đơn

    @Operation(summary = "[CHƯA DÙNG] Xuất hóa đơn cho hợp đồng",
            description = "Mỗi hợp đồng chỉ có một hóa đơn. Số tiền sao chép từ giá trị "
                        + "hợp đồng đúng một lần tại đây. ĐÃ ĐƯỢC THAY THẾ: frontend không gọi "
                        + "endpoint này trực tiếp nữa — POST /billing/registrations/{id}/confirm "
                        + "gọi lại đúng hàm này ở tầng service, gộp chung với thu tiền.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','SALE','ACCOUNTANT','ADMIN')")
    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> xuatHoaDon(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody XuatHoaDonRequest req) {
        var inv = billing.xuatHoaDon(req.registrationId(), userId, req.dueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(inv);
    }

    @Operation(summary = "[CHƯA DÙNG] Chi tiết hóa đơn",
            description = "Hiện chưa có nơi nào trong frontend gọi API này — bảng Công nợ ở "
                        + "trang Thu ngân đã có đủ thông tin cần thiết từ GET /billing/invoices/unpaid.")
    @GetMapping("/invoices/{id}")
    public InvoiceResponse chiTiet(@PathVariable Long id) {
        return billing.chiTietHoaDon(id);
    }

    @Operation(summary = "Các khoản thu của một hóa đơn")
    @GetMapping("/invoices/{id}/payments")
    public List<PaymentResponse> khoanThu(@PathVariable Long id) {
        return billing.khoanThuCuaHoaDon(id);
    }

    @Operation(summary = "Danh sách công nợ", description = "Hóa đơn chưa thu đủ")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','SALE','ACCOUNTANT','ADMIN')")
    @GetMapping("/invoices/unpaid")
    public List<InvoiceResponse> congNo() {
        return billing.congNo();
    }

    // -------------------------------------------------------- xác nhận gói tập

    @Operation(summary = "Xác nhận gói tập",
            description = "Một lần bấm cho lễ tân: gộp xuất hóa đơn và thu đủ tiền, hợp đồng "
                        + "TỰ kích hoạt ngay sau đó. Dùng sau khi đã cầm tiền mặt hoặc thấy "
                        + "tiền chuyển khoản về.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ACCOUNTANT','ADMIN')")
    @PostMapping("/registrations/{registrationId}/confirm")
    public RegistrationResponse xacNhanGoiTap(@PathVariable Long registrationId,
                                              @AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody XacNhanGoiTapRequest req) {
        return billing.xacNhanGoiTap(registrationId, userId, PaymentMethod.valueOf(req.method()));
    }

    // ---------------------------------------------------------------- thu tiền

    @Operation(summary = "Thu tiền",
            description = "Thu đủ thì hợp đồng TỰ kích hoạt và sổ cái tự được cấp buổi — "
                        + "cả ba việc nằm trong một giao dịch.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ACCOUNTANT','ADMIN')")
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> thuTien(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody ThuTienRequest req) {
        var p = billing.thuTien(req.invoiceId(), req.amount(),
                PaymentMethod.valueOf(req.method()), userId, req.transferContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @Operation(summary = "[CHƯA DÙNG] Hoàn tiền",
            description = "Ghi bút toán ÂM trỏ về khoản thu gốc. Bắt buộc có lý do và người "
                        + "duyệt. Backend đã hoàn chỉnh và có test, nhưng CHƯA có màn hình nào "
                        + "ở frontend cho kế toán bấm hoàn tiền — đây là màn hình còn thiếu, "
                        + "không phải endpoint thừa.")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @PostMapping("/payments/{id}/refund")
    public PaymentResponse hoanTien(@PathVariable Long id,
                                    @AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody HoanTienRequest req) {
        return billing.hoanTien(id, req.amount(), req.reason(), userId);
    }

    // ------------------------------------------------------------ ca làm việc

    @Operation(summary = "Mở ca làm việc",
            description = "Phải mở ca trước khi thu tiền mặt, nếu không khoản thu sẽ nằm "
                        + "ngoài mọi lần đối soát.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @PostMapping("/cash-shifts/open")
    public ResponseEntity<CashShiftResponse> moCa(@AuthenticationPrincipal Long userId,
                                                  @RequestBody MoCaRequest req) {
        var ca = billing.moCa(userId, req.openingBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(ca);
    }

    @Operation(summary = "Đóng ca và đối soát tiền mặt",
            description = "Lệch giữa số đếm được và số theo sổ thì BẮT BUỘC ghi lý do.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @PostMapping("/cash-shifts/close")
    public CashShiftResponse dongCa(@AuthenticationPrincipal Long userId,
                                    @RequestBody DongCaRequest req) {
        return billing.dongCa(userId, req.countedCash(), req.reason());
    }

    @Operation(summary = "Ca đang mở của tôi",
            description = "204 nếu chưa mở ca nào — KHÔNG trả 200 kèm body rỗng, vì fetch() "
                        + "phía frontend gọi res.json() vô điều kiện và sẽ ném lỗi parse.")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    @GetMapping("/cash-shifts/current")
    public ResponseEntity<CashShiftResponse> caHienTai(@AuthenticationPrincipal Long userId) {
        CashShiftResponse ca = billing.caHienTai(userId);
        return ca == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ca);
    }

    // ---------------------------------------------------------------- yêu cầu

    public record XacNhanGoiTapRequest(
            @Pattern(regexp = "CASH|BANK_TRANSFER|VIETQR|CARD_POS|E_WALLET|GATEWAY",
                     message = "Hình thức thanh toán không hợp lệ") String method) {}

    public record XuatHoaDonRequest(
            @NotNull(message = "Vui lòng chọn hợp đồng") Long registrationId,
            LocalDate dueDate) {}

    public record ThuTienRequest(
            @NotNull(message = "Vui lòng chọn hóa đơn") Long invoiceId,
            @NotNull @Positive(message = "Số tiền phải lớn hơn 0") BigDecimal amount,
            @Pattern(regexp = "CASH|BANK_TRANSFER|VIETQR|CARD_POS|E_WALLET|GATEWAY",
                     message = "Hình thức thanh toán không hợp lệ") String method,
            @Size(max = 255) String transferContent) {}

    public record HoanTienRequest(
            @NotNull @Positive BigDecimal amount,
            @NotBlank(message = "Hoàn tiền bắt buộc ghi lý do") @Size(max = 255) String reason) {}

    public record MoCaRequest(@PositiveOrZero BigDecimal openingBalance) {}

    public record DongCaRequest(
            @NotNull @PositiveOrZero BigDecimal countedCash,
            @Size(max = 255) String reason) {}
}
