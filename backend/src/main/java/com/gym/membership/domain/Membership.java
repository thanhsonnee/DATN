package com.gym.membership.domain;

import com.gym.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** Gói tập trong danh mục đang bán. */
@Entity
@Table(name = "memberships")
@Getter
@Setter
public class Membership extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 20)
    private PackageType packageType;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "session_count")
    private Integer sessionCount;

    /** Giá niêm yết HIỆN TẠI. Lịch sử giá nằm ở snapshot trong hợp đồng. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "includes_trainer", nullable = false)
    private Boolean includesTrainer = false;

    /**
     * Chỉ dùng cho gói HYBRID: phần giá trị thuộc về PT.
     * CSDL bắt buộc phải có giá trị khi packageType = HYBRID.
     */
    @Column(name = "pt_value_ratio", precision = 4, scale = 3)
    private BigDecimal ptValueRatio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "area_codes", columnDefinition = "jsonb")
    private String areaCodes;

    @Column(name = "max_freeze_days", nullable = false)
    private Integer maxFreezeDays = 0;

    @Column(name = "is_refundable", nullable = false)
    private Boolean isRefundable = false;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;
}
