package com.gym.training.api.dto;

import com.gym.training.domain.PtSession;

import java.time.OffsetDateTime;

/** Buổi tập trả về cho giao diện. */
public record PtSessionResponse(
        Long id,
        Long memberId,
        String memberName,
        Long trainerId,
        String trainerName,
        Long registrationId,
        String sessionType,
        OffsetDateTime scheduledStart,
        OffsetDateTime scheduledEnd,
        OffsetDateTime actualStart,
        OffsetDateTime actualEnd,
        String roomName,
        String status,
        String rejectReason,

        /** Hai mốc xác nhận — buổi chỉ hoàn thành khi CẢ HAI đều có giá trị. */
        OffsetDateTime trainerConfirmedAt,
        OffsetDateTime memberConfirmedAt,
        boolean autoConfirmed,

        String noShowBy,
        String cancelledBy,
        String cancelReason,
        boolean isLateCancel,
        String note
) {
    public static PtSessionResponse from(PtSession s) {
        return new PtSessionResponse(
                s.getId(),
                s.getMember().getId(), s.getMember().getPerson().getFullName(),
                s.getTrainer().getId(), s.getTrainer().getPerson().getFullName(),
                s.getRegistration().getId(),
                s.getSessionType().name(),
                s.getScheduledStart(), s.getScheduledEnd(),
                s.getActualStart(), s.getActualEnd(),
                s.getRoomName(),
                s.getStatus().name(),
                s.getRejectReason(),
                s.getTrainerConfirmedAt(), s.getMemberConfirmedAt(), s.getAutoConfirmed(),
                s.getNoShowBy() == null ? null : s.getNoShowBy().name(),
                s.getCancelledBy() == null ? null : s.getCancelledBy().name(),
                s.getCancelReason(),
                s.getIsLateCancel(),
                s.getNote());
    }
}
