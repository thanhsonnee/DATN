package com.gym.checkin.job;

import com.gym.checkin.service.CheckInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInJobs {

    private final CheckInService checkInService;

    /**
     * Tự đóng các lượt quên quét ra, chạy lúc 23 giờ sau khi phòng gym đóng cửa.
     */
    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void dongLuotQuenQuetRa() {
        try {
            checkInService.tuDongDongLuotQuenQuetRa();
        } catch (Exception ex) {
            log.error("Job đóng lượt check-in gặp lỗi", ex);
        }
    }
}
