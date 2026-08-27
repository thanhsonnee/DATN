package com.gym.billing.domain;

import com.gym.common.domain.BaseEntity;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import com.gym.membership.domain.Registration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Hóa đơn phải thu.
 *
 * <p>Chỉ giữ các số RIÊNG của hóa đơn: phải thu bao nhiêu, đã thu, còn nợ.
 * Giá niêm yết và tiền giảm nằm ở {@link Registration} — không chép lại để
 * tránh có hai nguồn sự thật cho cùng một con số.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice extends BaseEntity {

    @Column(name = "invoice_no", nullable = false, length = 30)
    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Column(length = 255)
    private String description;

    /** Sao chép từ {@code registration.finalPrice} đúng một lần lúc xuất hóa đơn. */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** CSDL tự tính: {@code totalAmount - paidAmount}. */
    @Column(name = "balance_due", insertable = false, updatable = false, precision = 14, scale = 2)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private BigDecimal balanceDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt = OffsetDateTime.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    public boolean daThuDu() {
        return paidAmount.compareTo(totalAmount) >= 0;
    }
}
