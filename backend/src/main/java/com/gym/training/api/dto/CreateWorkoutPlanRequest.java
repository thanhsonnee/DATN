package com.gym.training.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Yêu cầu soạn giáo án mới. trainerId lấy từ người đang đăng nhập, không nhận từ client. */
public record CreateWorkoutPlanRequest(

        @NotNull(message = "Vui lòng chọn hợp đồng")
        Long registrationId,

        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 200)
        String title,

        @NotBlank(message = "Vui lòng nhập nội dung giáo án")
        String content
) {}
