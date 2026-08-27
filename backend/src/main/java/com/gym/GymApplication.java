package com.gym;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hệ thống quản lý phòng gym — backend nghiệp vụ.
 *
 * <p>Kiến trúc: modular monolith. Mỗi module nằm trong một package con của
 * {@code com.gym} và chỉ giao tiếp với module khác qua tầng {@code service},
 * không gọi thẳng {@code repository} của nhau.
 *
 * <p>Nhờ vậy các bất biến tài chính (sổ cái buổi tập, ghi nhận doanh thu) nằm
 * gọn trong một transaction, mà sau này vẫn tách được thành dịch vụ riêng.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GymApplication {

    public static void main(String[] args) {
        SpringApplication.run(GymApplication.class, args);
    }
}
