package com.gym.billing.domain;

import com.gym.identity.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Chi tiết lương của từng nhân viên trong đợt chạy lương.
 */
@Entity
@Table(name = "payroll_items")
@Getter
@Setter
public class PayrollItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(name = "pt_sessions_count", nullable = false)
    private Integer ptSessionsCount = 0;

    @Column(name = "pt_commission", nullable = false, precision = 14, scale = 2)
    private BigDecimal ptCommission = BigDecimal.ZERO;

    @Column(name = "sales_contracts_count", nullable = false)
    private Integer salesContractsCount = 0;

    @Column(name = "sales_commission", nullable = false, precision = 14, scale = 2)
    private BigDecimal salesCommission = BigDecimal.ZERO;

    @Column(name = "bonus_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
