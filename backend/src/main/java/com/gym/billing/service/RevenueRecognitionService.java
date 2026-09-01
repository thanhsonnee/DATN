package com.gym.billing.service;

import com.gym.billing.api.dto.RevenueReportResponse;
import com.gym.billing.api.dto.RevenueScheduleResponse;
import com.gym.billing.domain.*;
import com.gym.billing.repository.PaymentRepository;
import com.gym.billing.repository.RevenueScheduleRepository;
import com.gym.membership.domain.Registration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * E3: Ghi nhận doanh thu dồn tích (Accrual Accounting / Revenue Recognition).
 *
 * <p>Phân bổ giá trị hợp đồng đều theo thời gian (Straight-line) vào các tháng.
 * Phản ánh đúng chuẩn mực kế toán: thu tiền 12 tháng không được ghi nhận 100% doanh thu
 * ngay trong tháng đầu tiên mà phải phân bổ dồn tích từng tháng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueRecognitionService {

    private final RevenueScheduleRepository scheduleRepo;
    private final PaymentRepository paymentRepo;

    /**
     * Tự động sinh lịch ghi nhận doanh thu khi hợp đồng được kích hoạt.
     */
    @Transactional
    public List<RevenueSchedule> generateSchedule(Registration registration, Invoice invoice) {
        if (registration == null || registration.getFinalPrice() == null
                || registration.getFinalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        var existing = scheduleRepo.findByRegistrationId(registration.getId());
        if (!existing.isEmpty()) {
            return existing;
        }

        LocalDate start = registration.getStartDate() != null ? registration.getStartDate() : LocalDate.now();
        LocalDate end = registration.getEndDate() != null ? registration.getEndDate() :
                (registration.getDurationDays() != null ? start.plusDays(registration.getDurationDays() - 1L) : start.plusMonths(1));

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        int months = Math.max(1, (int) Math.round((double) days / 30.0));

        BigDecimal totalPrice = registration.getFinalPrice();
        BigDecimal monthlyAmount = totalPrice.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        List<RevenueSchedule> list = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();

        for (int i = 0; i < months; i++) {
            RevenueSchedule rs = new RevenueSchedule();
            rs.setRegistration(registration);
            rs.setInvoice(invoice);

            LocalDate scheduleDate = start.plusMonths(i);
            rs.setScheduleDate(scheduleDate);
            rs.setRecognitionMethod(RevenueMethod.STRAIGHT_LINE);

            BigDecimal currentAmount;
            if (i == months - 1) {
                // Tháng cuối bù chênh lệch làm tròn
                currentAmount = totalPrice.subtract(accumulated);
            } else {
                currentAmount = monthlyAmount;
                accumulated = accumulated.add(currentAmount);
            }
            rs.setAmount(currentAmount);

            // Nếu ngày ghi nhận là hiện tại hoặc trong quá khứ -> Nhận ngay
            if (!scheduleDate.isAfter(today)) {
                rs.setStatus(RevenueScheduleStatus.RECOGNIZED);
                rs.setRecognizedAt(OffsetDateTime.now());
            } else {
                rs.setStatus(RevenueScheduleStatus.PENDING);
            }

            rs.setNote(String.format("Phân bổ tháng %d/%d - HĐ %s", i + 1, months, registration.getRegistrationCode()));
            list.add(rs);
        }

        list = scheduleRepo.saveAll(list);
        log.info("Đã tạo {} kỳ ghi nhận doanh thu cho hợp đồng {}", list.size(), registration.getRegistrationCode());
        return list;
    }

    /**
     * Quét và chuyển trạng thái ghi nhận doanh thu các kỳ đến hạn (chạy mỗi đêm lúc 00:05).
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public int scanAndRecognize() {
        LocalDate today = LocalDate.now();
        List<RevenueSchedule> pending = scheduleRepo.findByStatusAndScheduleDateLessThanEqual(
                RevenueScheduleStatus.PENDING, today);

        for (RevenueSchedule rs : pending) {
            rs.setStatus(RevenueScheduleStatus.RECOGNIZED);
            rs.setRecognizedAt(OffsetDateTime.now());
        }

        if (!pending.isEmpty()) {
            scheduleRepo.saveAll(pending);
            log.info("Đã ghi nhận dồn tích {} kỳ doanh thu đến hạn ngày {}", pending.size(), today);
        }
        return pending.size();
    }

    /**
     * Báo cáo đối chiếu Doanh thu thực thu (Cash) vs Doanh thu dồn tích (Accrual).
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse getRevenueReport(int month, int year) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);

        OffsetDateTime fromTime = startOfMonth.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
        OffsetDateTime toTime = endOfMonth.atTime(23, 59, 59).atOffset(ZoneOffset.ofHours(7));

        BigDecimal cashCollected = paymentRepo.sumCollectedBetween(fromTime, toTime);
        BigDecimal accrualRecognized = scheduleRepo.sumRecognizedBetween(startOfMonth, endOfMonth);
        BigDecimal deferredRevenue = scheduleRepo.sumPendingBetween(startOfMonth, endOfMonth);

        List<RevenueScheduleResponse> schedules = scheduleRepo.findByScheduleDateBetween(startOfMonth, endOfMonth)
                .stream()
                .map(RevenueScheduleResponse::from)
                .toList();

        return new RevenueReportResponse(
                month,
                year,
                cashCollected != null ? cashCollected : BigDecimal.ZERO,
                accrualRecognized != null ? accrualRecognized : BigDecimal.ZERO,
                deferredRevenue != null ? deferredRevenue : BigDecimal.ZERO,
                schedules
        );
    }
}
