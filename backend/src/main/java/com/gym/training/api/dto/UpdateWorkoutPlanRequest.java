package com.gym.training.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Yêu cầu sửa giáo án cũ — không đổi được registrationId/trainer sau khi đã tạo. */
public record UpdateWorkoutPlanRequest(

        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 200)
        String title,

        @NotBlank(message = "Vui lòng nhập nội dung giáo án")
        String content
) {}
