package com.gym.feedback.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEquipmentRequest(
        @NotBlank(message = "Vui lòng nhập tên thiết bị")
        @Size(max = 120, message = "Tên thiết bị không quá 120 ký tự")
        String name,

        @Size(max = 120, message = "Tên khu/phòng không quá 120 ký tự")
        String roomName,

        @Size(max = 255, message = "Ghi chú không quá 255 ký tự")
        String note
) {}
