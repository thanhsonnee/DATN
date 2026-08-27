package com.gym.billing.repository;

import com.gym.billing.domain.Invoice;
import com.gym.billing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNoAndDeletedAtIsNull(String invoiceNo);

    Optional<Invoice> findByRegistrationIdAndDeletedAtIsNull(Long registrationId);

    List<Invoice> findByMemberIdAndDeletedAtIsNullOrderByIssuedAtDesc(Long memberId);

    /** Công nợ — hóa đơn chưa thu đủ, để lễ tân nhắc khách. */
    List<Invoice> findByStatusInAndDeletedAtIsNullOrderByDueDateAsc(List<InvoiceStatus> statuses);
}
