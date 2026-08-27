package com.gym.training.job;

import com.gym.training.service.PtSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Việc chạy tự động theo lịch của module buổi tập. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PtSessionJobs {

    private final PtSessionService ptSessionService;

    /**
     * Tự duyệt buổi tập mà hội viên không phản hồi quá 24 giờ.
     *
     * <p>Chạy 2 giờ sáng — giờ gần như không ai dùng hệ thống, nên việc quét dữ
     * liệu không làm chậm ai.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void tuDuyetBuoiQuaHan() {
        try {
            int soLuong = ptSessionService.tuDuyetBuoiQuaHan();
            if (soLuong > 0) log.info("Job đêm đã tự duyệt {} buổi tập", soLuong);
        } catch (Exception ex) {
            // Job lỗi không được làm sập ứng dụng — ghi log để còn biết mà xử lý
            log.error("Job tự duyệt buổi tập gặp lỗi", ex);
        }
    }
}
