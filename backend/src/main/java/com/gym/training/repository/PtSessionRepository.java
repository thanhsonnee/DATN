package com.gym.training.repository;

import com.gym.training.domain.PtSession;
import com.gym.training.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface PtSessionRepository extends JpaRepository<PtSession, Long> {

    List<PtSession> findByMemberIdAndDeletedAtIsNullOrderByScheduledStartDesc(Long memberId);

    List<PtSession> findByTrainerIdAndDeletedAtIsNullOrderByScheduledStartDesc(Long trainerId);

    List<PtSession> findByRegistrationIdAndDeletedAtIsNull(Long registrationId);

    /**
     * Buổi huấn luyện viên đã xác nhận nhưng hội viên chưa phản hồi quá hạn.
     * Job đêm dùng danh sách này để tự duyệt.
     */
    @Query("SELECT s FROM PtSession s "
         + "WHERE s.status = com.gym.training.domain.SessionStatus.SCHEDULED "
         + "  AND s.trainerConfirmedAt IS NOT NULL "
         + "  AND s.memberConfirmedAt IS NULL "
         + "  AND s.trainerConfirmedAt < :truoc "
         + "  AND s.deletedAt IS NULL")
    List<PtSession> timBuoiChoHoiVienXacNhan(@Param("truoc") OffsetDateTime truoc);

    /** Đếm buổi theo loại trong một hợp đồng — phục vụ kiểm tra hạn mức. */
    long countByRegistrationIdAndStatus(Long registrationId, SessionStatus status);

    /** Đếm số buổi PT hoàn thành của HLV trong khoảng thời gian để tính hoa hồng lương. */
    @Query("SELECT COUNT(s) FROM PtSession s "
         + "WHERE s.trainer.id = :trainerId "
         + "  AND s.status = com.gym.training.domain.SessionStatus.COMPLETED "
         + "  AND s.scheduledStart >= :from AND s.scheduledStart <= :to "
         + "  AND s.deletedAt IS NULL")
    long countCompletedSessionsByTrainerBetween(@Param("trainerId") Long trainerId,
                                                @Param("from") OffsetDateTime from,
                                                @Param("to") OffsetDateTime to);
}
