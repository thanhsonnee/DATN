package com.gym.sales.repository;

import com.gym.sales.domain.Lead;
import com.gym.sales.domain.LeadSource;
import com.gym.sales.domain.LeadStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByDeletedAtIsNullOrderByCreatedAtDesc();

    List<Lead> findByStageAndDeletedAtIsNullOrderByCreatedAtDesc(LeadStage stage);

    List<Lead> findByAssignedToIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long employeeId);

    List<Lead> findByAssignedToIdAndStageAndDeletedAtIsNullOrderByCreatedAtDesc(Long employeeId, LeadStage stage);

    Optional<Lead> findByPersonIdAndDeletedAtIsNull(Long personId);

    List<Lead> findByPersonIdAndStageNotInAndDeletedAtIsNull(Long personId, List<LeadStage> closedStages);

    @Query("SELECT l.stage, COUNT(l) FROM Lead l WHERE l.deletedAt IS NULL GROUP BY l.stage")
    List<Object[]> countByStage();

    @Query("SELECT l.lostReason, COUNT(l) FROM Lead l WHERE l.stage = 'LOST' AND l.deletedAt IS NULL GROUP BY l.lostReason")
    List<Object[]> countLostByReason();

    /**
     * Danh sách tài khoản đã đăng ký app nhưng chưa từng mua gói (chưa có trong bảng members).
     */
    @Query(value = """
        SELECT p.id, p.full_name, p.phone, p.email, u.created_at
        FROM users u
        JOIN persons p ON p.id = u.person_id
        LEFT JOIN members m ON m.person_id = p.id
        WHERE u.primary_role = 'MEMBER'
          AND m.id IS NULL
          AND u.deleted_at IS NULL
        ORDER BY u.created_at DESC
    """, nativeQuery = true)
    List<Object[]> findAppRegisteredUsersWithoutMembership();
}
