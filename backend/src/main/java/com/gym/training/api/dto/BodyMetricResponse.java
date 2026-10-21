package com.gym.training.api.dto;

import com.gym.training.domain.BodyMetric;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Chỉ số cơ thể trả về cho giao diện, đã kèm BMI/BMR/WHR tính sẵn. */
public record BodyMetricResponse(
        Long id,
        Long memberId,
        BigDecimal weightKg,
        BigDecimal heightCm,
        BigDecimal waistCm,
        BigDecimal hipCm,
        BigDecimal bmi,
        Integer bmrKcal,
        BigDecimal whr,
        OffsetDateTime measuredAt
) {
    public static BodyMetricResponse from(BodyMetric m) {
        return new BodyMetricResponse(
                m.getId(), m.getMember().getId(),
                m.getWeightKg(), m.getHeightCm(), m.getWaistCm(), m.getHipCm(),
                m.getBmi(), m.getBmrKcal(), m.getWhr(), m.getMeasuredAt());
    }
}
