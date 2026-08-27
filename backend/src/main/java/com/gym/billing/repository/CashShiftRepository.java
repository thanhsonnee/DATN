package com.gym.billing.repository;

import com.gym.billing.domain.CashShift;
import com.gym.billing.domain.CashShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    /** Ca đang mở của một nhân viên. Mỗi người tối đa một ca cùng lúc. */
    Optional<CashShift> findByEmployeeIdAndStatus(Long employeeId, CashShiftStatus status);

    List<CashShift> findByEmployeeIdOrderByOpenedAtDesc(Long employeeId);

    List<CashShift> findByStatusOrderByOpenedAtDesc(CashShiftStatus status);
}
