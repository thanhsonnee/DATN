package com.gym.feedback.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Hội viên tự sửa phản hồi của mình — chỉ đổi được nội dung và (với loại TRAINER) đánh giá lại số sao. */
public record UpdateFeedbackRequest(
        @NotBlank(message = "Vui lòng mô tả nội dung phản hồi")
        @Size(max = 2000, message = "Nội dung không quá 2000 ký tự")
        String description,

        @Min(value = 1, message = "Đánh giá thấp nhất là 1 sao")
        @Max(value = 5, message = "Đánh giá cao nhất là 5 sao")
        Integer rating
) {}
