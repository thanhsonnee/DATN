package com.gym.membership.domain;

import com.gym.common.domain.BaseEntity;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * HỢP ĐỒNG — bảng trung tâm của hệ thống.
 *
 * <p>Gánh thêm hai vai trò: khuyến mãi (qua {@code discountAmount}/{@code discountReason})
 * và bảo lưu (nhóm trường {@code freeze*}, mỗi hợp đồng tối đa một lần).
 */
@Entity
@Table(name = "registrations")
@Getter
@Setter
public class Registration extends BaseEntity {

    @Column(name = "registration_code", nullable = false, length = 30)
    private String registrationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sold_by")
    private Employee soldBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_trainer_id")
    private Employee assignedTrainer;

    // ---- Snapshot điều khoản: sao chép lúc ký, KHÔNG đọc ngược từ memberships ----

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 20)
    private PackageType packageType;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "sessions_total")
    private Integer sessionsTotal;

    @Column(name = "list_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_approved_by")
    private User discountApprovedBy;

    @Column(name = "final_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal finalPrice;

    // ---- Thời gian ----

    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Bị ĐẨY LÙI thêm {@code freezeDays} khi kỳ bảo lưu được duyệt. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.PENDING_PAYMENT;

    @Column(name = "close_reason", length = 255)
    private String closeReason;

    @Column(columnDefinition = "text")
    private String note;

    // ---- Bảo lưu (thay bảng registration_freezes) ----

    @Column(name = "freeze_from_date")
    private LocalDate freezeFromDate;

    @Column(name = "freeze_to_date")
    private LocalDate freezeToDate;

    /** CSDL TỰ TÍNH, không nhập tay được nên không thể lệch với hai mốc ngày. */
    @Column(name = "freeze_days", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Integer freezeDays;

    @Column(name = "freeze_reason", length = 255)
    private String freezeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_reason_type", length = 20)
    private FreezeReasonType freezeReasonType;

    @Column(name = "freeze_attachment_key", length = 500)
    private String freezeAttachmentKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freeze_requested_by")
    private User freezeRequestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freeze_approved_by")
    private User freezeApprovedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_status", length = 20)
    private FreezeStatus freezeStatus;

    @Column(name = "freeze_ended_early_at")
    private LocalDate freezeEndedEarlyAt;

    /** Hợp đồng có cho phép check-in vào phòng tập không. */
    public boolean allowsCheckIn() {
        return status == RegistrationStatus.ACTIVE;
    }
}
