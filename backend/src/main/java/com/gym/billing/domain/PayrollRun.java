package com.gym.billing.domain;

import com.gym.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đợt chạy lương hàng tháng của phòng gym.
 */
@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_code", nullable = false, unique = true, length = 30)
    private String payrollCode;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "total_base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalBaseSalary = BigDecimal.ZERO;

    @Column(name = "total_commission", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCommission = BigDecimal.ZERO;

    @Column(name = "total_bonus", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalBonus = BigDecimal.ZERO;

    @Column(name = "total_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeduction = BigDecimal.ZERO;

    @Column(name = "total_net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNetSalary = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayrollStatus status = PayrollStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "payrollRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
