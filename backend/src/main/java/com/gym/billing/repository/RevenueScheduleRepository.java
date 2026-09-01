package com.gym.billing.repository;

import com.gym.billing.domain.RevenueSchedule;
import com.gym.billing.domain.RevenueScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface RevenueScheduleRepository extends JpaRepository<RevenueSchedule, Long> {

    List<RevenueSchedule> findByRegistrationId(Long registrationId);

    List<RevenueSchedule> findByStatusAndScheduleDateLessThanEqual(
            RevenueScheduleStatus status, LocalDate scheduleDate);

    List<RevenueSchedule> findByScheduleDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(rs.amount), 0) FROM RevenueSchedule rs " +
           "WHERE rs.status = 'RECOGNIZED' AND rs.scheduleDate BETWEEN :from AND :to")
    BigDecimal sumRecognizedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(rs.amount), 0) FROM RevenueSchedule rs " +
           "WHERE rs.status = 'PENDING' AND rs.scheduleDate BETWEEN :from AND :to")
    BigDecimal sumPendingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
