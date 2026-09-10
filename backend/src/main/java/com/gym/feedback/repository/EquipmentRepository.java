package com.gym.feedback.repository;

import com.gym.feedback.domain.Equipment;
import com.gym.feedback.domain.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByDeletedAtIsNullOrderByNameAsc();

    /** Thiết bị cần sửa — dùng cho dashboard Admin. */
    long countByStatusAndDeletedAtIsNull(EquipmentStatus status);
}
