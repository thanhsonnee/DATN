package com.gym.checkin.api.dto;

import java.time.OffsetDateTime;

/**
 * Hội viên tự hỏi lại: yêu cầu tự check-in trên app đang ở đâu.
 *
 * <p>App gọi lặp lại (poll) API này sau khi bấm "Tôi đã đến phòng tập", để tự
 * cập nhật giao diện ngay khi lễ tân xác nhận — không cần hội viên tự bấm làm
 * mới hay hỏi lễ tân bằng miệng.
 */
public record CheckInSelfStatusResponse(
        /** PENDING: còn trong hàng đợi chờ lễ tân. CONFIRMED: lễ tân đã xử lý xong (cho vào hoặc từ chối).
         *  NONE: chưa từng gửi yêu cầu, hoặc yêu cầu cũ đã quá hạn mà không ai xử lý. */
        String status,
        boolean choPhepVao,
        String thongBao,
        /** Lý do lễ tân từ chối (nếu có) — chỉ khác null khi {@code result} thật là DENIED_MANUAL. */
        String lyDo,
        OffsetDateTime checkedInAt
) {
    public static CheckInSelfStatusResponse pending(String thongBao, boolean choPhepVao) {
        return new CheckInSelfStatusResponse("PENDING", choPhepVao, thongBao, null, null);
    }

    public static CheckInSelfStatusResponse confirmed(CheckInResponse c) {
        String lyDo = "DENIED_MANUAL".equals(c.result()) ? c.incidentNote() : null;
        return new CheckInSelfStatusResponse("CONFIRMED", c.choPhepVao(), c.thongBao(), lyDo, c.checkedInAt());
    }

    public static CheckInSelfStatusResponse none() {
        return new CheckInSelfStatusResponse("NONE", false,
                "Chưa gửi yêu cầu, hoặc yêu cầu trước đã hết hạn", null, null);
    }
}
