package com.gym.feedback.api.dto;

import com.gym.feedback.domain.FeedbackStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateFeedbackStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái mới")
        FeedbackStatus status,

        @Size(max = 500, message = "Ghi chú xử lý không quá 500 ký tự")
        String resolutionNote,

        /** Chỉ áp dụng khi đóng phản hồi FACILITY đã sửa xong — tự sinh dòng chi phí. */
        @DecimalMin(value = "0", message = "Chi phí sửa chữa không được âm")
        BigDecimal repairCost
) {}
