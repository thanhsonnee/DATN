package com.gym.membership.api.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Yêu cầu bảo lưu gói tập. Mỗi hợp đồng chỉ được bảo lưu một lần. */
public record FreezeRequest(

        @NotNull(message = "Vui lòng chọn ngày bắt đầu bảo lưu")
        @Future(message = "Ngày bắt đầu bảo lưu phải ở tương lai")
        LocalDate fromDate,

        @NotNull(message = "Vui lòng chọn ngày kết thúc bảo lưu")
        @Future(message = "Ngày kết thúc bảo lưu phải ở tương lai")
        LocalDate toDate,

        @NotBlank(message = "Vui lòng nhập lý do bảo lưu")
        @Size(max = 255)
        String reason,

        @Pattern(regexp = "PERSONAL|MEDICAL|TRAVEL|OTHER", message = "Loại lý do không hợp lệ")
        String reasonType,

        /** Key ảnh giấy tờ y tế trong MinIO, dùng khi lý do là MEDICAL. */
        @Size(max = 500)
        String attachmentKey
) {}
