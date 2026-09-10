package com.gym.admin.service;

import com.gym.admin.api.dto.AdminDashboardResponse;
import com.gym.billing.domain.CashShiftStatus;
import com.gym.billing.domain.Invoice;
import com.gym.billing.domain.InvoiceStatus;
import com.gym.billing.domain.PayrollStatus;
import com.gym.billing.repository.CashShiftRepository;
import com.gym.billing.repository.InvoiceRepository;
import com.gym.billing.repository.PayrollRunRepository;
import com.gym.billing.service.RevenueRecognitionService;
import com.gym.checkin.repository.CheckInRepository;
import com.gym.feedback.domain.EquipmentStatus;
import com.gym.feedback.domain.FeedbackStatus;
import com.gym.feedback.repository.EquipmentRepository;
import com.gym.feedback.repository.FeedbackRepository;
import com.gym.identity.repository.MemberRepository;
import com.gym.membership.domain.RegistrationStatus;
import com.gym.membership.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Tổng hợp số liệu cho dashboard Admin — hai nhóm ưu tiên đã chốt: Hội viên &amp;
 * Doanh thu, Vận hành hôm nay. Chỉ ĐỌC, không ghi gì — mọi số liệu tính lại
 * mỗi lần gọi, không cache (quy mô một phòng gym, không cần tối ưu sớm).
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneOffset GIO_VN = ZoneOffset.ofHours(7);

    private final MemberRepository memberRepo;
    private final RegistrationRepository registrationRepo;
    private final InvoiceRepository invoiceRepo;
    private final RevenueRecognitionService revenueService;
    private final CheckInRepository checkInRepo;
    private final CashShiftRepository cashShiftRepo;
    private final FeedbackRepository feedbackRepo;
    private final EquipmentRepository equipmentRepo;
    private final PayrollRunRepository payrollRunRepo;

    @Transactional(readOnly = true)
    public AdminDashboardResponse tongQuan() {
        LocalDate homNay = LocalDate.now();
        return new AdminDashboardResponse(
                tinhMembersOverview(homNay),
                tinhRevenueOverview(homNay),
                tinhOperationsToday(homNay));
    }

    private AdminDashboardResponse.MembersOverview tinhMembersOverview(LocalDate homNay) {
        long dangHoatDong = registrationRepo.countDistinctActiveMembers();

        LocalDate dauThang = homNay.withDayOfMonth(1);
        LocalDate cuoiThang = dauThang.plusMonths(1).minusDays(1);
        long moiTrongThang = memberRepo.countByJoinDateBetweenAndDeletedAtIsNull(dauThang, cuoiThang);

        long sapHetHan = registrationRepo.findExpiringBetween(homNay, homNay.plusDays(7)).size();
        long dangBaoLuu = registrationRepo.countByStatusAndDeletedAtIsNull(RegistrationStatus.FROZEN);

        return new AdminDashboardResponse.MembersOverview(dangHoatDong, moiTrongThang, sapHetHan, dangBaoLuu);
    }

    private AdminDashboardResponse.RevenueOverview tinhRevenueOverview(LocalDate homNay) {
        var baoCao = revenueService.getRevenueReport(homNay.getMonthValue(), homNay.getYear());

        List<Invoice> congNo = invoiceRepo.findByStatusInAndDeletedAtIsNullOrderByDueDateAsc(
                List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE));
        BigDecimal tongCongNo = congNo.stream()
                .map(Invoice::getBalanceDue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminDashboardResponse.RevenueOverview(
                baoCao.totalCashCollected(),
                baoCao.totalAccrualRecognized(),
                baoCao.totalDeferredRevenue(),
                tongCongNo,
                congNo.size());
    }

    private AdminDashboardResponse.OperationsToday tinhOperationsToday(LocalDate homNay) {
        OffsetDateTime dauNgay = homNay.atStartOfDay().atOffset(GIO_VN);

        long checkInHomNay = checkInRepo.countByCheckedInAtAfter(dauNgay);
        long biTuChoiHomNay = checkInRepo.countBiTuChoiAfter(dauNgay);

        long caDangMo = cashShiftRepo.countByStatus(CashShiftStatus.OPEN);
        long caLechQuy = cashShiftRepo.countByStatus(CashShiftStatus.DISCREPANCY);

        List<FeedbackStatus> dangMo = List.of(
                FeedbackStatus.OPEN, FeedbackStatus.IN_PROGRESS, FeedbackStatus.WAITING_PARTS);
        long phanHoiDangMo = feedbackRepo.countByStatusIn(dangMo);
        long phanHoiKhanCap = feedbackRepo.countByUrgentTrueAndStatusIn(dangMo);

        long thietBiCanSua = equipmentRepo.countByStatusAndDeletedAtIsNull(EquipmentStatus.NEEDS_REPAIR);
        long luongChuaDuyet = payrollRunRepo.countByStatus(PayrollStatus.DRAFT);

        return new AdminDashboardResponse.OperationsToday(
                checkInHomNay, biTuChoiHomNay, caDangMo, caLechQuy,
                phanHoiDangMo, phanHoiKhanCap, thietBiCanSua, luongChuaDuyet);
    }
}
