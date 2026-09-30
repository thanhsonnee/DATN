package com.gym.membership.api.dto;

import com.gym.membership.domain.Membership;

import java.math.BigDecimal;

/** Gói tập nhìn từ phía Admin — đủ field để quản lý, khác bản công khai (MembershipResponse). */
public record MembershipAdminResponse(
        Long id,
        String code,
        String name,
        String packageType,
        Integer durationDays,
        Integer sessionCount,
        BigDecimal price,
        Boolean includesTrainer,
        BigDecimal ptValueRatio,
        Integer maxFreezeDays,
        Boolean isRefundable,
        String description,
        Integer displayOrder,
        String status
) {
    public static MembershipAdminResponse from(Membership m) {
        return new MembershipAdminResponse(
                m.getId(), m.getCode(), m.getName(), m.getPackageType().name(),
                m.getDurationDays(), m.getSessionCount(), m.getPrice(),
                m.getIncludesTrainer(), m.getPtValueRatio(), m.getMaxFreezeDays(),
                m.getIsRefundable(), m.getDescription(), m.getDisplayOrder(),
                m.getStatus().name());
    }
}
