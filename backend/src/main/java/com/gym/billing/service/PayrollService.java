package com.gym.billing.service;

import com.gym.billing.api.dto.CalculatePayrollRequest;
import com.gym.billing.api.dto.PayrollItemResponse;
import com.gym.billing.api.dto.PayrollRunResponse;
import com.gym.billing.api.dto.UpdatePayrollItemRequest;
import com.gym.billing.domain.PayrollItem;
import com.gym.billing.domain.PayrollRun;
import com.gym.billing.domain.PayrollStatus;
import com.gym.billing.repository.PaymentRepository;
import com.gym.billing.repository.PayrollItemRepository;
import com.gym.billing.repository.PayrollRunRepository;
import com.gym.common.exception.ApiException;
import com.gym.common.util.CodeGenerator;
import com.gym.identity.domain.Department;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.User;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.training.repository.PtSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * E4: Tính và chạy bảng lương hàng tháng (Payroll Runs).
 *
 * <p>Tự động tổng hợp:
 * <ul>
 *   <li>Lương cơ bản từ hồ sơ nhân sự (employees.base_salary)</li>
 *   <li>Hoa hồng PT từ số buổi COMPLETED trong tháng (100.000 đ / buổi)</li>
 *   <li>Hoa hồng Sales/Lễ tân: 5% tổng doanh số thu tiền trong tháng</li>
 *   <li>Thưởng/Phạt điều chỉnh thủ công trước khi duyệt</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRunRepository payrollRunRepo;
    private final PayrollItemRepository payrollItemRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final PtSessionRepository ptSessionRepo;
    private final PaymentRepository paymentRepo;
    private final CodeGenerator codeGenerator;

    /**
     * Tính toán bảng lương nháp cho một tháng.
     */
    @Transactional
    public PayrollRunResponse calculatePayroll(CalculatePayrollRequest req, Long actorUserId) {
        int month = req.periodMonth();
        int year = req.periodYear();

        var existingOpt = payrollRunRepo.findByPeriodMonthAndPeriodYear(month, year);
        PayrollRun run;
        if (existingOpt.isPresent()) {
            run = existingOpt.get();
            if (run.getStatus() == PayrollStatus.APPROVED || run.getStatus() == PayrollStatus.PAID) {
                throw ApiException.conflict("PAYROLL_ALREADY_LOCKED",
                        "Bảng lương tháng " + month + "/" + year + " đã được duyệt hoặc chi trả, không thể tính lại");
            }
            // Xóa các item cũ để tính lại
            run.getItems().clear();
        } else {
            run = new PayrollRun();
            run.setPayrollCode(codeGenerator.nextPayrollCode(year, month));
            run.setPeriodMonth(month);
            run.setPeriodYear(year);
            run.setStatus(PayrollStatus.DRAFT);
            if (actorUserId != null) {
                User creator = userRepo.findById(actorUserId).orElse(null);
                run.setCreatedBy(creator);
            }
        }

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);
        OffsetDateTime fromTime = startOfMonth.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
        OffsetDateTime toTime = endOfMonth.atTime(23, 59, 59).atOffset(ZoneOffset.ofHours(7));

        List<Employee> employees = employeeRepo.findAll().stream()
                .filter(e -> e.getDeletedAt() == null)
                .toList();

        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalDeduction = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        List<PayrollItem> items = new ArrayList<>();

        for (Employee emp : employees) {
            PayrollItem item = new PayrollItem();
            item.setPayrollRun(run);
            item.setEmployee(emp);

            BigDecimal base = emp.getBaseSalary() != null ? emp.getBaseSalary() : BigDecimal.ZERO;
            item.setBaseSalary(base);

            // 1. Hoa hồng PT (HLV)
            int ptCount = 0;
            BigDecimal ptCommission = BigDecimal.ZERO;
            if (emp.getDepartment() == Department.TRAINING) {
                long completed = ptSessionRepo.countCompletedSessionsByTrainerBetween(emp.getId(), fromTime, toTime);
                ptCount = (int) completed;
                ptCommission = BigDecimal.valueOf(ptCount * 100_000L).setScale(2, RoundingMode.HALF_UP);
            }
            item.setPtSessionsCount(ptCount);
            item.setPtCommission(ptCommission);

            // 2. Hoa hồng Sales / Lễ tân
            int salesCount = 0;
            BigDecimal salesCommission = BigDecimal.ZERO;
            if (emp.getDepartment() == Department.SALES || emp.getDepartment() == Department.FRONT_DESK) {
                var userOpt = userRepo.findByPersonIdAndDeletedAtIsNull(emp.getPerson().getId());
                if (userOpt.isPresent()) {
                    BigDecimal collected = paymentRepo.sumCollectedByUserBetween(userOpt.get().getId(), fromTime, toTime);
                    if (collected != null && collected.compareTo(BigDecimal.ZERO) > 0) {
                        salesCommission = collected.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
            item.setSalesContractsCount(salesCount);
            item.setSalesCommission(salesCommission);

            item.setBonusAmount(BigDecimal.ZERO);
            item.setDeductionAmount(BigDecimal.ZERO);

            BigDecimal empCommission = ptCommission.add(salesCommission);
            BigDecimal net = base.add(empCommission);
            item.setNetSalary(net);

            totalBase = totalBase.add(base);
            totalCommission = totalCommission.add(empCommission);
            totalNet = totalNet.add(net);

            items.add(item);
        }

        run.getItems().addAll(items);
        run.setTotalBaseSalary(totalBase);
        run.setTotalCommission(totalCommission);
        run.setTotalBonus(totalBonus);
        run.setTotalDeduction(totalDeduction);
        run.setTotalNetSalary(totalNet);

        run = payrollRunRepo.save(run);
        log.info("Tính bảng lương {} cho tháng {}/{}: {} nhân viên, tổng lương {}",
                run.getPayrollCode(), month, year, items.size(), totalNet);

        return PayrollRunResponse.from(run);
    }

    /**
     * Chỉnh sửa thưởng/phạt hoặc ghi chú cho một dòng lương nhân viên.
     */
    @Transactional
    public PayrollItemResponse updatePayrollItem(Long itemId, UpdatePayrollItemRequest req) {
        PayrollItem item = payrollItemRepo.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng lương"));

        PayrollRun run = item.getPayrollRun();
        if (run.getStatus() != PayrollStatus.DRAFT) {
            throw ApiException.badRequest("NOT_EDITABLE", "Chỉ có thể chỉnh sửa khi bảng lương ở trạng thái DRAFT");
        }

        if (req.bonusAmount() != null) {
            item.setBonusAmount(req.bonusAmount().setScale(2, RoundingMode.HALF_UP));
        }
        if (req.deductionAmount() != null) {
            item.setDeductionAmount(req.deductionAmount().setScale(2, RoundingMode.HALF_UP));
        }
        if (req.note() != null) {
            item.setNote(req.note());
        }

        BigDecimal net = item.getBaseSalary()
                .add(item.getPtCommission())
                .add(item.getSalesCommission())
                .add(item.getBonusAmount())
                .subtract(item.getDeductionAmount());
        item.setNetSalary(net);

        item = payrollItemRepo.save(item);

        // Cập nhật lại tổng bảng lương
        List<PayrollItem> allItems = payrollItemRepo.findByPayrollRunId(run.getId());
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalComm = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalDeduct = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (PayrollItem it : allItems) {
            totalBase = totalBase.add(it.getBaseSalary());
            totalComm = totalComm.add(it.getPtCommission()).add(it.getSalesCommission());
            totalBonus = totalBonus.add(it.getBonusAmount());
            totalDeduct = totalDeduct.add(it.getDeductionAmount());
            totalNet = totalNet.add(it.getNetSalary());
        }

        run.setTotalBaseSalary(totalBase);
        run.setTotalCommission(totalComm);
        run.setTotalBonus(totalBonus);
        run.setTotalDeduction(totalDeduct);
        run.setTotalNetSalary(totalNet);
        payrollRunRepo.save(run);

        return PayrollItemResponse.from(item);
    }

    /**
     * Duyệt bảng lương.
     */
    @Transactional
    public PayrollRunResponse approvePayroll(Long payrollId, Long actorUserId) {
        PayrollRun run = payrollRunRepo.findById(payrollId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bảng lương"));

        if (run.getStatus() != PayrollStatus.DRAFT) {
            throw ApiException.conflict("INVALID_STATE", "Chỉ có thể duyệt bảng lương ở trạng thái DRAFT");
        }

        run.setStatus(PayrollStatus.APPROVED);
        userRepo.findById(actorUserId).ifPresent(run::setApprovedBy);
        run.setApprovedAt(OffsetDateTime.now());

        run = payrollRunRepo.save(run);
        log.info("Bảng lương {} đã được duyệt bởi userId={}", run.getPayrollCode(), actorUserId);
        return PayrollRunResponse.from(run);
    }

    /**
     * Đánh dấu đã chi trả lương.
     */
    @Transactional
    public PayrollRunResponse payPayroll(Long payrollId, Long actorUserId) {
        PayrollRun run = payrollRunRepo.findById(payrollId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bảng lương"));

        if (run.getStatus() != PayrollStatus.APPROVED) {
            throw ApiException.badRequest("NOT_APPROVED", "Bảng lương phải được duyệt trước khi chi trả");
        }

        run.setStatus(PayrollStatus.PAID);
        run.setPaidAt(OffsetDateTime.now());

        run = payrollRunRepo.save(run);
        log.info("Bảng lương {} đã được thanh toán", run.getPayrollCode());
        return PayrollRunResponse.from(run);
    }

    /**
     * Danh sách các đợt chạy lương.
     */
    @Transactional(readOnly = true)
    public List<PayrollRunResponse> getAllPayrollRuns() {
        return payrollRunRepo.findAllByOrderByPeriodYearDescPeriodMonthDesc().stream()
                .map(PayrollRunResponse::from)
                .toList();
    }

    /**
     * Chi tiết một đợt chạy lương kèm danh sách nhân viên.
     */
    @Transactional(readOnly = true)
    public PayrollRunResponse getPayrollDetail(Long id) {
        PayrollRun run = payrollRunRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bảng lương"));
        return PayrollRunResponse.from(run);
    }

    /**
     * Nhân viên/HLV xem phiếu lương cá nhân của tháng.
     */
    @Transactional(readOnly = true)
    public PayrollItemResponse getMyPayslip(Long actorUserId, int month, int year) {
        User user = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        Employee emp = employeeRepo.findByPersonIdAndDeletedAtIsNull(user.getPerson().getId())
                .orElseThrow(() -> ApiException.badRequest("NOT_AN_EMPLOYEE", "Tài khoản của bạn không phải là nhân viên"));

        PayrollItem item = payrollItemRepo.findApprovedPayslip(emp.getId(), month, year)
                .orElseThrow(() -> ApiException.notFound("Chưa có phiếu lương đã duyệt cho tháng " + month + "/" + year));

        return PayrollItemResponse.from(item);
    }
}
