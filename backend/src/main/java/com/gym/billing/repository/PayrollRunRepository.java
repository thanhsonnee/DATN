package com.gym.billing.repository;

import com.gym.billing.domain.PayrollRun;
import com.gym.billing.domain.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    Optional<PayrollRun> findByPeriodMonthAndPeriodYearAndStatusNot(
            Integer periodMonth, Integer periodYear, PayrollStatus status);

    Optional<PayrollRun> findByPeriodMonthAndPeriodYear(Integer periodMonth, Integer periodYear);

    List<PayrollRun> findAllByOrderByPeriodYearDescPeriodMonthDesc();
}
