package com.gym.billing.domain;

import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Khoản thu hoặc khoản hoàn tiền.
 *
 * <p>Hoàn tiền ghi là bút toán <b>âm</b> thay vì tách bảng riêng, nhờ vậy cộng
 * dồn ra ngay số tiền phòng gym thực sự giữ lại.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, length = 30)
    private String paymentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Bắt buộc với tiền mặt. Chuyển khoản để trống vì không nằm trong két. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_shift_id")
    private CashShift cashShift;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType = PaymentType.PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /** Dương là thu vào, âm là hoàn trả. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(length = 30)
    private String provider;

    /** Mã giao dịch phía cổng trả về. Duy nhất để webhook gửi lại không ghi hai lần. */
    @Column(name = "provider_txn_id", length = 100)
    private String providerTxnId;

    @Column(name = "transfer_content", length = 255)
    private String transferContent;

    @Column(name = "bank_account", length = 50)
    private String bankAccount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_of_payment_id")
    private Payment refundOfPayment;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    @Column(name = "refund_penalty", precision = 14, scale = 2)
    private BigDecimal refundPenalty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;
}
