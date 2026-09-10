package com.gym.settings.domain;

import com.gym.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Một tham số nghiệp vụ chỉnh được qua API (khoá — giá trị dạng chuỗi), thay
 * cho hằng số hardcode trong code. Kiểu dữ liệu thật (Integer, BigDecimal,
 * LocalTime...) do tầng service diễn giải, không ràng buộc ở đây.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
