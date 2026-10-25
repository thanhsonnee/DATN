package com.gym.training.domain;

import com.gym.identity.domain.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Chỉ số cơ thể hội viên tự nhập.
 *
 * <p>{@code bmi}/{@code bmrKcal}/{@code whr} do {@code BodyMetricService} tự
 * tính lúc lưu — hội viên chỉ nhập cân nặng/chiều cao/vòng eo/vòng hông, không
 * tự tính các chỉ số dẫn xuất.
 */
@Entity
@Table(name = "body_metrics")
@Getter
@Setter
public class BodyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", nullable = false, precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "waist_cm", precision = 5, scale = 2)
    private BigDecimal waistCm;

    @Column(name = "hip_cm", precision = 5, scale = 2)
    private BigDecimal hipCm;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal bmi;

    @Column(name = "bmr_kcal")
    private Integer bmrKcal;

    @Column(precision = 4, scale = 3)
    private BigDecimal whr;

    @Column(name = "measured_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime measuredAt;
}
