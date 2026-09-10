package com.gym.billing.repository;

import com.gym.billing.domain.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {

    List<PayrollItem> findByPayrollRunId(Long payrollRunId);

    @Query("SELECT pi FROM PayrollItem pi JOIN pi.payrollRun pr " +
           "WHERE pi.employee.id = :employeeId AND pr.periodMonth = :month AND pr.periodYear = :year " +
           "AND pr.status IN ('APPROVED', 'PAID')")
    Optional<PayrollItem> findApprovedPayslip(
            @Param("employeeId") Long employeeId,
            @Param("month") Integer month,
            @Param("year") Integer year);

    @Query("SELECT COALESCE(SUM(pr.totalNetSalary), 0) FROM PayrollRun pr " +
           "WHERE pr.periodMonth = :month AND pr.periodYear = :year AND pr.status IN ('APPROVED', 'PAID')")
    BigDecimal sumPaidSalaryInPeriod(@Param("month") Integer month, @Param("year") Integer year);
}
