package com.gym.feedback.api.dto;

import com.gym.feedback.domain.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sửa thiết bị — cho phép Admin chuyển trạng thái tay (vd. RETIRED khi thanh lý),
 *  dù bình thường NEEDS_REPAIR do luồng phản hồi hội viên tự gán. */
public record UpdateEquipmentRequest(
        @NotBlank(message = "Vui lòng nhập tên thiết bị")
        @Size(max = 120, message = "Tên thiết bị không quá 120 ký tự")
        String name,

        @Size(max = 120, message = "Tên khu/phòng không quá 120 ký tự")
        String roomName,

        @NotNull(message = "Vui lòng chọn trạng thái")
        EquipmentStatus status,

        @Size(max = 255, message = "Ghi chú không quá 255 ký tự")
        String note
) {}
