package com.gym.settings.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSystemSettingRequest(
        @NotBlank(message = "Giá trị không được để trống")
        String value
) {}
