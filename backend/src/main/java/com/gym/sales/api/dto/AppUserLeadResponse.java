package com.gym.sales.api.dto;

import java.time.OffsetDateTime;

public record AppUserLeadResponse(
        Long personId,
        String fullName,
        String phone,
        String email,
        OffsetDateTime registeredAt,
        Long daysSinceRegistration
) {}
