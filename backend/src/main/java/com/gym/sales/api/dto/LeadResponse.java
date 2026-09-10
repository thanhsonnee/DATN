package com.gym.sales.api.dto;

import com.gym.sales.domain.Lead;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LeadResponse(
        Long id,
        Long personId,
        String fullName,
        String phone,
        String email,
        String source,
        Long interestedMembershipId,
        String interestedMembershipName,
        String interestedMembershipCode,
        BigDecimal interestedMembershipPrice,
        Long assignedToId,
        String assignedToName,
        String assignedToCode,
        String stage,
        String lostReason,
        OffsetDateTime lastContactAt,
        String lastContactNote,
        LocalDate nextFollowUp,
        OffsetDateTime createdAt
) {
    public static LeadResponse from(Lead l) {
        var p = l.getPerson();
        var m = l.getInterestedMembership();
        var emp = l.getAssignedTo();

        return new LeadResponse(
                l.getId(),
                p.getId(),
                p.getFullName(),
                p.getPhone(),
                p.getEmail(),
                l.getSource().name(),
                m != null ? m.getId() : null,
                m != null ? m.getName() : null,
                m != null ? m.getCode() : null,
                m != null ? m.getPrice() : null,
                emp != null ? emp.getId() : null,
                emp != null ? emp.getPerson().getFullName() : null,
                emp != null ? emp.getEmployeeCode() : null,
                l.getStage().name(),
                l.getLostReason() != null ? l.getLostReason().name() : null,
                l.getLastContactAt(),
                l.getLastContactNote(),
                l.getNextFollowUp(),
                l.getCreatedAt()
        );
    }
}
