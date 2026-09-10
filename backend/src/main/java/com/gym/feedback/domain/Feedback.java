package com.gym.feedback.domain;

import com.gym.common.domain.BaseEntity;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Phản hồi của hội viên — đánh giá PT, báo hỏng thiết bị, vệ sinh, góp ý chung.
 *
 * <p>Định tuyến theo {@link FeedbackType} xảy ra ngay lúc tạo (xem {@code FeedbackService}),
 * không phải một bước riêng — hội viên gửi xong là hệ thống đã phản ứng (đổi trạng thái
 * thiết bị, cộng dồn điểm PT).
 */
@Entity
@Table(name = "feedbacks")
@Getter
@Setter
public class Feedback extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 20)
    private FeedbackType feedbackType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id")
    private Employee trainer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    private Integer rating;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @Column(name = "is_urgent", nullable = false)
    private boolean urgent = false;

    @Column(name = "repair_cost", precision = 14, scale = 2)
    private BigDecimal repairCost;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
