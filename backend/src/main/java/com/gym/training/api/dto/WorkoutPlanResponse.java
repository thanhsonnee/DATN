package com.gym.training.api.dto;

import com.gym.training.domain.WorkoutPlan;

import java.time.OffsetDateTime;
import java.util.List;

/** Giáo án trả về cho giao diện. {@code completedDates} đã giải mã từ JSONB sang danh sách chuỗi ngày. */
public record WorkoutPlanResponse(
        Long id,
        Long registrationId,
        Long trainerId,
        String trainerName,
        String title,
        String content,
        List<String> completedDates,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WorkoutPlanResponse from(WorkoutPlan p, List<String> ngayDaTap) {
        return new WorkoutPlanResponse(
                p.getId(),
                p.getRegistration().getId(),
                p.getTrainer().getId(), p.getTrainer().getPerson().getFullName(),
                p.getTitle(), p.getContent(),
                ngayDaTap,
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
