package com.gym.settings.api.dto;

import com.gym.settings.domain.SystemSetting;

import java.time.OffsetDateTime;

public record SystemSettingResponse(
        String key,
        String value,
        String description,
        OffsetDateTime updatedAt,
        String updatedByUsername
) {
    public static SystemSettingResponse from(SystemSetting s) {
        return new SystemSettingResponse(
                s.getSettingKey(),
                s.getSettingValue(),
                s.getDescription(),
                s.getUpdatedAt(),
                s.getUpdatedBy() != null ? s.getUpdatedBy().getUsername() : null
        );
    }
}
