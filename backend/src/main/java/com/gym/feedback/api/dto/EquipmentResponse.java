package com.gym.feedback.api.dto;

import com.gym.feedback.domain.Equipment;

public record EquipmentResponse(
        Long id,
        String name,
        String roomName,
        String status,
        String note
) {
    public static EquipmentResponse from(Equipment e) {
        return new EquipmentResponse(e.getId(), e.getName(), e.getRoomName(),
                e.getStatus().name(), e.getNote());
    }
}
