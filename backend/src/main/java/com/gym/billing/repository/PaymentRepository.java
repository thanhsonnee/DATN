package com.gym.billing.repository;

import com.gym.billing.domain.Payment;
import com.gym.billing.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
         + "WHERE p.cashShift.id = :shiftId "
         + "  AND p.status = com.gym.billing.domain.PaymentStatus.SUCCEEDED")
    BigDecimal tongTienMatTrongCa(@Param("shiftId") Long shiftId);

    /** Tổng đã thu của một hóa đơn, dùng để cập nhật lại số đã trả. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
         + "WHERE p.invoice.id = :invoiceId AND p.status = :status")
    BigDecimal tongDaThu(@Param("invoiceId") Long invoiceId, @Param("status") PaymentStatus status);
}
