package com.gym.sales.api.dto;

import com.gym.sales.domain.LeadStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateLeadContactRequest(
        @NotNull(message = "Vui lòng chọn giai đoạn phễu tiếp theo")
        LeadStage stage,

        @Size(max = 2000, message = "Nội dung trao đổi quá dài")
        String contactNote,

        LocalDate nextFollowUp,

        Long interestedMembershipId
) {}
