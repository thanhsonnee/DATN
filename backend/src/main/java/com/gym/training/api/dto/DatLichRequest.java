package com.gym.training.api.dto;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

/** Yêu cầu đặt lịch tập với huấn luyện viên. */
public record DatLichRequest(

        @NotNull(message = "Vui lòng chọn hợp đồng")
        Long registrationId,

        @NotNull(message = "Vui lòng chọn huấn luyện viên")
        Long trainerId,

        @NotNull(message = "Vui lòng chọn giờ bắt đầu")
        @Future(message = "Giờ tập phải ở tương lai")
        OffsetDateTime scheduledStart,

        @NotNull(message = "Vui lòng chọn giờ kết thúc")
        OffsetDateTime scheduledEnd,

        /** Bỏ trống thì mặc định là buổi có trả phí. */
        @Pattern(regexp = "PAID_PT|COMPLIMENTARY|TRIAL|ORIENTATION|MAKEUP|ASSESSMENT",
                 message = "Loại buổi tập không hợp lệ")
        String sessionType,

        @Size(max = 120)
        String roomName
) {}
