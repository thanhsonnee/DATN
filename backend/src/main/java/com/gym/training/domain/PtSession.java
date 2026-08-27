package com.gym.training.domain;

import com.gym.common.domain.BaseEntity;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import com.gym.membership.domain.Registration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Buổi tập cá nhân với huấn luyện viên, gánh luôn vòng đời đặt lịch.
 *
 * <p><b>Xác nhận hai chiều</b> là cơ chế chống khai khống: buổi chỉ được tính khi
 * cả huấn luyện viên và hội viên cùng xác nhận. Nút bấm của huấn luyện viên là
 * điều kiện khởi động — không bấm thì không có gì xảy ra, nên buổi không dạy sẽ
 * không bao giờ tự động được trả công.
 */
@Entity
@Table(name = "pt_sessions")
@Getter
@Setter
public class PtSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** Người THỰC TẾ dạy. Tiền công tính cho người này. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id", nullable = false)
    private Employee trainer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType = SessionType.PAID_PT;

    @Column(name = "scheduled_start", nullable = false)
    private OffsetDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private OffsetDateTime scheduledEnd;

    @Column(name = "actual_start")
    private OffsetDateTime actualStart;

    @Column(name = "actual_end")
    private OffsetDateTime actualEnd;

    @Column(name = "room_name", length = 120)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.PENDING_TRAINER;

    // ---- Vòng đời đặt lịch ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    // ---- Xác nhận hai chiều ----

    @Column(name = "trainer_confirmed_at")
    private OffsetDateTime trainerConfirmedAt;

    @Column(name = "member_confirmed_at")
    private OffsetDateTime memberConfirmedAt;

    /** Hội viên không phản hồi trong 24h thì hệ thống tự duyệt. Cờ để kiểm toán. */
    @Column(name = "auto_confirmed", nullable = false)
    private Boolean autoConfirmed = false;

    // ---- Vắng mặt và hủy ----

    @Enumerated(EnumType.STRING)
    @Column(name = "no_show_by", length = 20)
    private ActorSide noShowBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private ActorSide cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** Hủy sát giờ thì mất buổi, không được hoàn lại vào sổ cái. */
    @Column(name = "is_late_cancel", nullable = false)
    private Boolean isLateCancel = false;

    @Column(columnDefinition = "text")
    private String note;

    /** Buổi này có trừ buổi của hội viên không. Chỉ buổi có trả phí mới trừ. */
    public boolean truBuoiCuaHoiVien() {
        return sessionType == SessionType.PAID_PT;
    }

    public boolean daXacNhanDuHaiBen() {
        return trainerConfirmedAt != null && memberConfirmedAt != null;
    }
}
