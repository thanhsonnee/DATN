package com.gym.feedback.api.dto;

import com.gym.feedback.domain.Feedback;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FeedbackResponse(
        Long id,
        Long memberId,
        String memberName,
        String feedbackType,
        Long trainerId,
        String trainerName,
        Long equipmentId,
        String equipmentName,
        Integer rating,
        String description,
        String status,
        boolean urgent,
        BigDecimal repairCost,
        String resolutionNote,
        String resolvedByName,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {
    public static FeedbackResponse from(Feedback f) {
        var trainer = f.getTrainer();
        var equipment = f.getEquipment();
        var resolvedBy = f.getResolvedBy();

        return new FeedbackResponse(
                f.getId(),
                f.getMember().getId(),
                f.getMember().getPerson().getFullName(),
                f.getFeedbackType().name(),
                trainer != null ? trainer.getId() : null,
                trainer != null ? trainer.getPerson().getFullName() : null,
                equipment != null ? equipment.getId() : null,
                equipment != null ? equipment.getName() : null,
                f.getRating(),
                f.getDescription(),
                f.getStatus().name(),
                f.isUrgent(),
                f.getRepairCost(),
                f.getResolutionNote(),
                resolvedBy != null ? resolvedBy.getPerson().getFullName() : null,
                f.getResolvedAt(),
                f.getCreatedAt()
        );
    }
}
