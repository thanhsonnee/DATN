package com.gym.billing.api;

import com.gym.billing.api.dto.*;
import com.gym.billing.domain.ExpenseCategory;
import com.gym.billing.service.ExpenseService;
import com.gym.billing.service.PayrollService;
import com.gym.billing.service.RevenueRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Tài chính & Kế toán", description = "Doanh thu dồn tích (E3), Bảng lương (E4), Chi phí & Lợi nhuận (E5)")
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final RevenueRecognitionService revenueService;
    private final PayrollService payrollService;
    private final ExpenseService expenseService;

    // =========================================================================
    // E3: Ghi nhận doanh thu dồn tích (Revenue Recognition)
    // =========================================================================

    @Operation(summary = "Quét ghi nhận doanh thu đến hạn")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @PostMapping("/revenue/scan-recognized")
    public ResponseEntity<Integer> scanRecognized() {
        int count = revenueService.scanAndRecognize();
        return ResponseEntity.ok(count);
    }

    @Operation(summary = "Báo cáo đối chiếu Doanh thu thực thu (Cash) vs Dồn tích (Accrual)")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @GetMapping("/revenue/report")
    public RevenueReportResponse getRevenueReport(@RequestParam int month, @RequestParam int year) {
        return revenueService.getRevenueReport(month, year);
    }

    // =========================================================================
    // E4: Chạy bảng lương hàng tháng (Payroll Runs)
    // =========================================================================

    @Operation(summary = "Tính toán bảng lương nháp theo tháng")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @PostMapping("/payrolls/calculate")
    public ResponseEntity<PayrollRunResponse> calculatePayroll(@AuthenticationPrincipal Long userId,
                                                               @Valid @RequestBody CalculatePayrollRequest req) {
        var res = payrollService.calculatePayroll(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Danh sách các đợt chạy lương")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @GetMapping("/payrolls")
    public List<PayrollRunResponse> getAllPayrollRuns() {
        return payrollService.getAllPayrollRuns();
    }

    @Operation(summary = "Chi tiết bảng lương và danh sách lương từng nhân viên")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @GetMapping("/payrolls/{id}")
    public PayrollRunResponse getPayrollDetail(@PathVariable Long id) {
        return payrollService.getPayrollDetail(id);
    }

    @Operation(summary = "Chỉnh sửa thưởng/phạt cho một nhân viên trong bảng lương DRAFT")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @PutMapping("/payrolls/items/{itemId}")
    public PayrollItemResponse updatePayrollItem(@PathVariable Long itemId,
                                                 @Valid @RequestBody UpdatePayrollItemRequest req) {
        return payrollService.updatePayrollItem(itemId, req);
    }

    @Operation(summary = "Duyệt bảng lương")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/payrolls/{id}/approve")
    public PayrollRunResponse approvePayroll(@PathVariable Long id,
                                             @AuthenticationPrincipal Long userId) {
        return payrollService.approvePayroll(id, userId);
    }

    @Operation(summary = "Xác nhận chi trả bảng lương")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @PostMapping("/payrolls/{id}/pay")
    public PayrollRunResponse payPayroll(@PathVariable Long id,
                                         @AuthenticationPrincipal Long userId) {
        return payrollService.payPayroll(id, userId);
    }

    @Operation(summary = "Xem phiếu lương cá nhân của nhân viên/HLV")
    @GetMapping("/payrolls/my-payslip")
    public PayrollItemResponse getMyPayslip(@AuthenticationPrincipal Long userId,
                                            @RequestParam int month,
                                            @RequestParam int year) {
        return payrollService.getMyPayslip(userId, month, year);
    }

    // =========================================================================
    // E5: Quản lý Chi phí & Báo cáo Lợi nhuận (P&L)
    // =========================================================================

    @Operation(summary = "Ghi nhận khoản chi mới")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @PostMapping("/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody CreateExpenseRequest req) {
        var res = expenseService.createExpense(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Tra cứu danh sách chi phí")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @GetMapping("/expenses")
    public List<ExpenseResponse> getExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ExpenseCategory category) {
        return expenseService.getExpenses(from, to, category);
    }

    @Operation(summary = "Xóa khoản chi")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Báo cáo Kết quả Kinh doanh (P&L: Thu - Chi - Lợi nhuận)")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    @GetMapping("/profit-loss")
    public ProfitLossReportResponse getProfitLossReport(@RequestParam int month, @RequestParam int year) {
        return expenseService.getProfitLossReport(month, year);
    }
}
