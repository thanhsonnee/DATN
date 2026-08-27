package com.gym.membership.api.dto;

import com.gym.membership.domain.Registration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Hợp đồng trả về cho giao diện. */
public record RegistrationResponse(
        Long id,
        String registrationCode,
        Long memberId,
        String memberCode,
        String memberName,
        Long membershipId,
        String membershipName,
        String packageType,
        Integer durationDays,
        Integer sessionsTotal,
        BigDecimal listPrice,
        BigDecimal discountAmount,
        String discountReason,
        BigDecimal finalPrice,
        LocalDate contractDate,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime activatedAt,
        String status,
        Freeze freeze
) {
    /** Thông tin kỳ bảo lưu. {@code null} khi hợp đồng chưa từng xin bảo lưu. */
    public record Freeze(
            LocalDate fromDate,
            LocalDate toDate,
            Integer days,
            String reason,
            String reasonType,
            String status
    ) {}

    public static RegistrationResponse from(Registration r) {
        Freeze freeze = r.getFreezeStatus() == null ? null : new Freeze(
                r.getFreezeFromDate(), r.getFreezeToDate(), r.getFreezeDays(),
                r.getFreezeReason(),
                r.getFreezeReasonType() == null ? null : r.getFreezeReasonType().name(),
                r.getFreezeStatus().name());

        return new RegistrationResponse(
                r.getId(), r.getRegistrationCode(),
                r.getMember().getId(), r.getMember().getMemberCode(),
                r.getMember().getPerson().getFullName(),
                r.getMembership().getId(), r.getMembership().getName(),
                r.getPackageType().name(), r.getDurationDays(), r.getSessionsTotal(),
                r.getListPrice(), r.getDiscountAmount(), r.getDiscountReason(), r.getFinalPrice(),
                r.getContractDate(), r.getStartDate(), r.getEndDate(), r.getActivatedAt(),
                r.getStatus().name(), freeze);
    }
}
