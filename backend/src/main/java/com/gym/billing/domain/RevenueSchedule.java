package com.gym.billing.domain;

import com.gym.membership.domain.Registration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Lịch ghi nhận doanh thu dồn tích (Accrual Accounting).
 * Phân bổ doanh thu theo từng tháng/kỳ trong suốt thời hạn hợp đồng.
 */
@Entity
@Table(name = "revenue_schedules")
@Getter
@Setter
public class RevenueSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RevenueScheduleStatus status = RevenueScheduleStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "recognition_method", nullable = false, length = 20)
    private RevenueMethod recognitionMethod = RevenueMethod.STRAIGHT_LINE;

    @Column(name = "recognized_at")
    private OffsetDateTime recognizedAt;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
