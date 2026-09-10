package com.gym.feedback.domain;

import com.gym.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Thiết bị phòng tập — bản rút gọn.
 *
 * <p>Thiết kế đầy đủ tách {@code equipment_types}/{@code equipment_items} để truy vết
 * từng cái thiết bị theo serial. Bản này gộp 1 bảng vì chưa cần truy vết tới mức đó —
 * chỉ cần biết "máy chạy bộ khu A đang hỏng" để ẩn khỏi lịch và tính chi phí sửa.
 */
@Entity
@Table(name = "equipment")
@Getter
@Setter
public class Equipment extends BaseEntity {

    private String name;

    private String roomName;

    @Enumerated(EnumType.STRING)
    private EquipmentStatus status = EquipmentStatus.ACTIVE;

    private String note;
}
