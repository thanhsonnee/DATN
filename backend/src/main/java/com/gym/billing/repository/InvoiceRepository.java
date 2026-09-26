package com.gym.billing.repository;

import com.gym.billing.domain.Invoice;
import com.gym.billing.domain.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNoAndDeletedAtIsNull(String invoiceNo);

    Optional<Invoice> findByRegistrationIdAndDeletedAtIsNull(Long registrationId);

    /**
     * Khóa dòng hóa đơn để tuần tự hóa các khoản thu đồng thời — cùng lý do như
     * {@code RegistrationRepository.khoaHopDong}: nếu không khóa, hai khoản thu
     * cùng lúc có thể mỗi khoản đều đọc số nợ còn lại trước khi khoản kia ghi
     * xong, nên mỗi khoản riêng lẻ đều qua được chặn OVERPAY dù cộng lại đã vượt
     * số nợ thật.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> khoaHoaDon(@Param("id") Long id);

    List<Invoice> findByMemberIdAndDeletedAtIsNullOrderByIssuedAtDesc(Long memberId);

    /** Công nợ — hóa đơn chưa thu đủ, để lễ tân nhắc khách. */
    List<Invoice> findByStatusInAndDeletedAtIsNullOrderByDueDateAsc(List<InvoiceStatus> statuses);
}
