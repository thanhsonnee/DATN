package com.gym.billing.service;

import com.gym.billing.api.dto.CreateExpenseRequest;
import com.gym.billing.api.dto.ExpenseResponse;
import com.gym.billing.api.dto.ProfitLossReportResponse;
import com.gym.billing.domain.Expense;
import com.gym.billing.domain.ExpenseCategory;
import com.gym.billing.domain.ExpenseStatus;
import com.gym.billing.domain.PaymentStatus;
import com.gym.billing.repository.ExpenseRepository;
import com.gym.billing.repository.PaymentRepository;
import com.gym.billing.repository.PayrollItemRepository;
import com.gym.billing.repository.RevenueScheduleRepository;
import com.gym.common.exception.ApiException;
import com.gym.common.util.CodeGenerator;
import com.gym.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E5: Quản lý Chi phí & Báo cáo Lợi nhuận phòng tập (Profit & Loss / P&L).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final PaymentRepository paymentRepo;
    private final RevenueScheduleRepository scheduleRepo;
    private final PayrollItemRepository payrollItemRepo;
    private final UserRepository userRepo;
    private final CodeGenerator codeGenerator;

    /**
     * Ghi nhận khoản chi mới.
     */
    @Transactional
    public ExpenseResponse createExpense(CreateExpenseRequest req, Long actorUserId) {
        Expense e = new Expense();
        e.setExpenseNo(codeGenerator.nextExpenseNo());
        e.setCategory(req.category());
        e.setTitle(req.title().trim());
        e.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        e.setSpentAt(req.spentAt());
        e.setPaymentMethod(req.paymentMethod());
        e.setReceiptUrl(req.receiptUrl() != null ? req.receiptUrl().trim() : null);
        e.setNote(req.note() != null ? req.note().trim() : null);
        e.setStatus(ExpenseStatus.APPROVED);

        if (actorUserId != null) {
            userRepo.findById(actorUserId).ifPresent(u -> {
                e.setSpentBy(u);
                e.setApprovedBy(u);
            });
        }

        Expense saved = expenseRepo.save(e);
        log.info("Ghi nhận chi phí {}: {} - {} đ", saved.getExpenseNo(), saved.getTitle(), saved.getAmount());
        return ExpenseResponse.from(saved);
    }

    /**
     * Tra cứu danh sách chi phí.
     */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(LocalDate from, LocalDate to, ExpenseCategory category) {
        LocalDate startDate = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate endDate = to != null ? to : LocalDate.now().withDayOfYear(LocalDate.now().lengthOfYear());

        List<Expense> list;
        if (category != null) {
            list = expenseRepo.findByCategoryAndSpentAtBetweenAndDeletedAtIsNullOrderBySpentAtDesc(category, startDate, endDate);
        } else {
            list = expenseRepo.findBySpentAtBetweenAndDeletedAtIsNullOrderBySpentAtDesc(startDate, endDate);
        }
        return list.stream().map(ExpenseResponse::from).toList();
    }

    /**
     * Xóa khoản chi.
     */
    @Transactional
    public void deleteExpense(Long id) {
        Expense e = expenseRepo.findById(id)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản chi"));

        e.setDeletedAt(OffsetDateTime.now());
        expenseRepo.save(e);
        log.info("Đã xóa khoản chi {}", e.getExpenseNo());
    }

    /**
     * Báo cáo Kết quả Kinh doanh (P&L: Thu - Chi - Lợi nhuận).
     */
    @Transactional(readOnly = true)
    public ProfitLossReportResponse getProfitLossReport(int month, int year) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);

        OffsetDateTime fromTime = startOfMonth.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
        OffsetDateTime toTime = endOfMonth.atTime(23, 59, 59).atOffset(ZoneOffset.ofHours(7));

        // 1. Doanh thu
        BigDecimal cashRev = paymentRepo.sumCollectedBetween(PaymentStatus.SUCCEEDED, fromTime, toTime);
        if (cashRev == null) cashRev = BigDecimal.ZERO;

        BigDecimal accrualRev = scheduleRepo.sumRecognizedBetween(startOfMonth, endOfMonth);
        if (accrualRev == null) accrualRev = BigDecimal.ZERO;

        // 2. Chi phí vận hành
        BigDecimal operatingExp = expenseRepo.sumExpensesBetween(startOfMonth, endOfMonth);
        if (operatingExp == null) operatingExp = BigDecimal.ZERO;

        // 3. Chi phí lương
        BigDecimal salaryExp = payrollItemRepo.sumPaidSalaryInPeriod(month, year);
        if (salaryExp == null) salaryExp = BigDecimal.ZERO;

        BigDecimal totalExp = operatingExp.add(salaryExp);

        // 4. Lợi nhuận
        BigDecimal netCash = cashRev.subtract(totalExp);
        BigDecimal netAccrual = accrualRev.subtract(totalExp);

        // 5. Chi tiết theo danh mục chi phí
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        List<Object[]> rows = expenseRepo.sumByCategoryBetween(startOfMonth, endOfMonth);
        for (Object[] r : rows) {
            if (r[0] != null && r[1] != null) {
                breakdown.put(r[0].toString(), (BigDecimal) r[1]);
            }
        }
        if (salaryExp.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.put("SALARY", salaryExp);
        }

        return new ProfitLossReportResponse(
                month,
                year,
                cashRev,
                accrualRev,
                operatingExp,
                salaryExp,
                totalExp,
                netCash,
                netAccrual,
                breakdown
        );
    }
}
