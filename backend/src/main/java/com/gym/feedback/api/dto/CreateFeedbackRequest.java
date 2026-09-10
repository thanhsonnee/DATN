package com.gym.feedback.api.dto;

import com.gym.feedback.domain.FeedbackType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
        @NotNull(message = "Vui lòng chọn loại phản hồi")
        FeedbackType feedbackType,

        /** Bắt buộc khi feedbackType = TRAINER. */
        Long trainerId,

        /** Bắt buộc khi feedbackType = FACILITY. */
        Long equipmentId,

        /** Chỉ có ý nghĩa khi đánh giá PT (feedbackType = TRAINER). */
        @Min(value = 1, message = "Đánh giá thấp nhất là 1 sao")
        @Max(value = 5, message = "Đánh giá cao nhất là 5 sao")
        Integer rating,

        @NotBlank(message = "Vui lòng mô tả nội dung phản hồi")
        @Size(max = 2000, message = "Nội dung không quá 2000 ký tự")
        String description
) {}
