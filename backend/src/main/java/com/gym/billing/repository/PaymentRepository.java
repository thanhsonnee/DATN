package com.gym.billing.repository;

import com.gym.billing.domain.Payment;
import com.gym.billing.domain.PaymentStatus;
import com.gym.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    Optional<Payment> findByProviderAndProviderTxnId(String provider, String providerTxnId);

    /**
     * Tổng tiền mặt đã thu trong một ca.
     *
     * <p>Cộng cả khoản hoàn tiền (số âm) nên ra đúng số tiền thực còn trong két.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
         + "WHERE p.cashShift.id = :shiftId AND p.status = :status")
    BigDecimal tongTienMatTrongCa(@Param("shiftId") Long shiftId, @Param("status") PaymentStatus status);

    /** Tổng đã thu của một hóa đơn, dùng để cập nhật lại số đã trả. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
         + "WHERE p.invoice.id = :invoiceId AND p.status = :status")
    BigDecimal tongDaThu(@Param("invoiceId") Long invoiceId, @Param("status") PaymentStatus status);

    /** Tổng tiền thực thu giữa 2 mốc thời gian. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
         + "WHERE p.status = :status "
         + "  AND p.paidAt >= :from AND p.paidAt <= :to")
    BigDecimal sumCollectedBetween(@Param("status") PaymentStatus status,
                                    @Param("from") java.time.OffsetDateTime from,
                                    @Param("to") java.time.OffsetDateTime to);

    /** Tổng doanh số do nhân viên thu/chốt trong khoảng thời gian. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
         + "WHERE p.status = :status "
         + "  AND p.receivedBy.id = :userId "
         + "  AND p.paidAt >= :from AND p.paidAt <= :to")
    BigDecimal sumCollectedByUserBetween(@Param("userId") Long userId,
                                         @Param("status") PaymentStatus status,
                                         @Param("from") java.time.OffsetDateTime from,
                                         @Param("to") java.time.OffsetDateTime to);

    /** Mọi khoản thu/hoàn tiền thành công trong 1 ngày — dùng cho màn Đối soát doanh thu (E6). */
    @Query("SELECT p FROM Payment p "
         + "WHERE p.status = :status AND p.paidAt >= :tu AND p.paidAt < :den "
         + "ORDER BY p.paidAt ASC")
    List<Payment> layDoanhThuTrongNgay(@Param("status") PaymentStatus status,
                                        @Param("tu") OffsetDateTime tu,
                                        @Param("den") OffsetDateTime den);

    /**
     * Kế toán xác nhận gộp 1 lần — đánh dấu mọi khoản CHƯA xác nhận trong ngày.
     * Khoản phát sinh sau khi đã xác nhận (vd. thu muộn) sẽ tự hiện lại "chưa
     * xác nhận" ở lần gọi {@link #layDoanhThuTrongNgay} tiếp theo — không cần
     * cơ chế khóa ngày riêng.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment p SET p.reconciledBy = :nguoiXacNhan, p.reconciledAt = :thoiDiem "
         + "WHERE p.status = :status AND p.paidAt >= :tu AND p.paidAt < :den AND p.reconciledAt IS NULL")
    int xacNhanDoanhThuTrongNgay(@Param("nguoiXacNhan") User nguoiXacNhan,
                                  @Param("thoiDiem") OffsetDateTime thoiDiem,
                                  @Param("status") PaymentStatus status,
                                  @Param("tu") OffsetDateTime tu,
                                  @Param("den") OffsetDateTime den);
}
