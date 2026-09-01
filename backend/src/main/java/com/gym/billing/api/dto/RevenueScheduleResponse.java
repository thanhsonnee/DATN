package com.gym.billing.api.dto;

import com.gym.billing.domain.RevenueSchedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RevenueScheduleResponse(
        Long id,
        Long registrationId,
        String registrationCode,
        String memberName,
        LocalDate scheduleDate,
        BigDecimal amount,
        String status,
        String recognitionMethod,
        OffsetDateTime recognizedAt,
        String note
) {
    public static RevenueScheduleResponse from(RevenueSchedule rs) {
        return new RevenueScheduleResponse(
                rs.getId(),
                rs.getRegistration().getId(),
                rs.getRegistration().getRegistrationCode(),
                rs.getRegistration().getMember().getPerson().getFullName(),
                rs.getScheduleDate(),
                rs.getAmount(),
                rs.getStatus().name(),
                rs.getRecognitionMethod().name(),
                rs.getRecognizedAt(),
                rs.getNote()
        );
    }
}
