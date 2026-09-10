package com.gym.membership.job;

import com.gym.membership.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Việc chạy tự động theo lịch của module hợp đồng & gói tập (A5 + A2). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationJobs {

    private final RegistrationService registrationService;

    /**
     * Tự động quét và đóng các hợp đồng hết hạn (A5) cũng như hủy hợp đồng bỏ ngang sau 48h (A2).
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Ho_Chi_Minh")
    public void xuLyHopDongJob() {
        try {
            int soLuong = registrationService.xuLyHetHan();
            if (soLuong > 0) {
                log.info("Job đêm đã đóng {} hợp đồng hết hạn", soLuong);
            }
            int soHuy = registrationService.huyHopDongBoNgang();
            if (soHuy > 0) {
                log.info("Job đêm đã hủy {} hợp đồng bỏ ngang quá 48h", soHuy);
            }
        } catch (Exception ex) {
            log.error("Job xử lý hợp đồng hết hạn/bỏ ngang gặp lỗi", ex);
        }
    }
}
