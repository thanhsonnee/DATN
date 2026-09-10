package com.gym.billing.repository;

import com.gym.billing.domain.Expense;
import com.gym.billing.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findBySpentAtBetweenAndDeletedAtIsNullOrderBySpentAtDesc(LocalDate from, LocalDate to);

    List<Expense> findByCategoryAndSpentAtBetweenAndDeletedAtIsNullOrderBySpentAtDesc(
            ExpenseCategory category, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.status = 'APPROVED' AND e.deletedAt IS NULL AND e.spentAt BETWEEN :from AND :to")
    BigDecimal sumExpensesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.status = 'APPROVED' AND e.deletedAt IS NULL AND e.spentAt BETWEEN :from AND :to " +
           "GROUP BY e.category ORDER BY e.category")
    List<Object[]> sumByCategoryBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
