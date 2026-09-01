package com.gym.sales.api.dto;

import com.gym.sales.domain.LostReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkLostRequest(
        @NotNull(message = "Bắt buộc chọn lý do mất khách để thống kê")
        LostReason lostReason,

        @Size(max = 1000)
        String note
) {}
