package com.gym.billing.domain;

import com.gym.identity.domain.Employee;
import com.gym.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Ca làm việc và đối soát tiền mặt của lễ tân.
 *
 * <p>Bảng duy nhất trong nhóm thanh toán nói về <b>nhân viên</b> chứ không phải
 * khách hàng. Mọi khoản thu tiền mặt bắt buộc gắn với một ca, nhờ vậy cuối ca
 * đếm được và phát hiện ngay nếu thiếu hụt.
 */
@Entity
@Table(name = "cash_shifts")
@Getter
@Setter
public class CashShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** Số tiền lễ tân ĐẾM ĐƯỢC lúc đóng ca. */
    @Column(name = "counted_cash", precision = 14, scale = 2)
    private BigDecimal countedCash;

    /** Số hệ thống tính ra: đầu ca cộng tổng thu tiền mặt trong ca. */
    @Column(name = "expected_cash", precision = 14, scale = 2)
    private BigDecimal expectedCash;

    /** CSDL tự tính: {@code countedCash - expectedCash}. Âm là thiếu tiền. */
    @Column(insertable = false, updatable = false, precision = 14, scale = 2)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private BigDecimal difference;

    @Column(name = "difference_reason", length = 255)
    private String differenceReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashShiftStatus status = CashShiftStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;
}
