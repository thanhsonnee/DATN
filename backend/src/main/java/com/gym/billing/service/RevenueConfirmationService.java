package com.gym.billing.service;

import com.gym.billing.api.dto.DailyRevenueResponse;
import com.gym.billing.api.dto.PaymentLineResponse;
import com.gym.billing.domain.Payment;
import com.gym.billing.domain.PaymentStatus;
import com.gym.billing.repository.PaymentRepository;
import com.gym.common.exception.ApiException;
import com.gym.identity.domain.User;
import com.gym.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * E6: Đối soát doanh thu theo ngày — minh bạch dòng tiền giữa Lễ tân/Sale (thu
 * tiền tại quầy) và Kế toán (kiểm soát tiền đã về đủ chưa).
 *
 * <p>Khác cơ chế đối soát ca ({@link BillingService} mở/đóng ca) vốn CHỈ áp
 * dụng tiền mặt trong két: đây là xác nhận MỌI hình thức thanh toán (tiền
 * mặt, chuyển khoản, thẻ...) — Kế toán xác nhận GỘP một lần cho cả ngày, không
 * duyệt từng khoản. Khoản phát sinh sau khi đã xác nhận tự hiện lại "chưa xác
 * nhận" ở lần xem tiếp theo, không có khái niệm "khóa ngày" cứng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueConfirmationService {

    private final PaymentRepository paymentRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public DailyRevenueResponse layDoanhThuNgay(LocalDate ngay) {
        return xayDungBaoCao(ngay);
    }

    /** Kế toán/Admin xác nhận gộp 1 lần: "đã nhận đủ tiền hôm nay". */
    @Transactional
    public DailyRevenueResponse xacNhan(LocalDate ngay, Long actorUserId) {
        User actor = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        OffsetDateTime tu = batDauNgay(ngay);
        OffsetDateTime den = batDauNgay(ngay.plusDays(1));

        int soDong = paymentRepo.xacNhanDoanhThuTrongNgay(
                actor, OffsetDateTime.now(), PaymentStatus.SUCCEEDED, tu, den);
        log.info("Kế toán {} xác nhận doanh thu ngày {}: {} khoản", actor.getUsername(), ngay, soDong);

        return xayDungBaoCao(ngay);
    }

    private DailyRevenueResponse xayDungBaoCao(LocalDate ngay) {
        OffsetDateTime tu = batDauNgay(ngay);
        OffsetDateTime den = batDauNgay(ngay.plusDays(1));

        List<Payment> ds = paymentRepo.layDoanhThuTrongNgay(PaymentStatus.SUCCEEDED, tu, den);

        BigDecimal tongCong = BigDecimal.ZERO;
        BigDecimal daXacNhan = BigDecimal.ZERO;
        for (Payment p : ds) {
            tongCong = tongCong.add(p.getAmount());
            if (p.getReconciledAt() != null) {
                daXacNhan = daXacNhan.add(p.getAmount());
            }
        }
        BigDecimal conLai = tongCong.subtract(daXacNhan);
        boolean daDu = ds.stream().noneMatch(p -> p.getReconciledAt() == null);
        List<PaymentLineResponse> lines = ds.stream().map(PaymentLineResponse::from).toList();

        return new DailyRevenueResponse(ngay, tongCong, daXacNhan, conLai, daDu, lines);
    }

    /** 00:00 giờ Việt Nam (UTC+7) của một ngày — biên để lọc theo ngày lịch VN, không phải UTC. */
    private OffsetDateTime batDauNgay(LocalDate ngay) {
        return ngay.atStartOfDay().atOffset(ZoneOffset.ofHours(7));
    }
}
