package com.gym.membership.api.dto;

import com.gym.membership.domain.Membership;

import java.math.BigDecimal;

/** Một gói tập trên bảng giá công khai. */
public record MembershipResponse(
        Long id,
        String code,
        String name,
        String packageType,
        Integer durationDays,
        Integer sessionCount,
        BigDecimal price,
        Boolean includesTrainer,
        Integer maxFreezeDays,
        Boolean isRefundable,
        String description
) {
    public static MembershipResponse from(Membership m) {
        return new MembershipResponse(
                m.getId(), m.getCode(), m.getName(), m.getPackageType().name(),
                m.getDurationDays(), m.getSessionCount(), m.getPrice(),
                m.getIncludesTrainer(), m.getMaxFreezeDays(), m.getIsRefundable(),
                m.getDescription());
    }
}
