package com.gym.checkin.domain;

import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import com.gym.membership.domain.Registration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;

/**
 * Một lượt vào phòng tập.
 *
 * <p>Ghi lại <b>cả lượt bị từ chối</b>, không chỉ lượt thành công. Nếu chỉ ghi
 * lượt vào được thì không biết hệ thống đã chặn được bao nhiêu ca gian lận —
 * mà đó mới là chỉ số đo hiệu quả của cơ chế kiểm soát.
 */
@Entity
@Table(name = "check_ins")
@Getter
@Setter
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id")
    private Registration registration;

    @Column(name = "checked_in_at", nullable = false)
    private OffsetDateTime checkedInAt = OffsetDateTime.now();

    @Column(name = "checked_out_at")
    private OffsetDateTime checkedOutAt;

    /**
     * Giờ ra do job đêm tự điền vì hội viên quên quét lúc về.
     *
     * <p>Cần cờ này để thống kê thời lượng tập biết đường loại ra — nếu không,
     * số liệu sẽ bị kéo theo giờ đóng cửa chứ không phản ánh hành vi thật.
     */
    @Column(name = "auto_closed", nullable = false)
    private Boolean autoClosed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInMethod method = CheckInMethod.QR_DYNAMIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckInResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", length = 30)
    private IncidentType incidentType;

    @Column(name = "incident_note", columnDefinition = "text")
    private String incidentNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_handled_by")
    private User incidentHandledBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;

    public boolean dangOTrongPhongTap() {
        return checkedOutAt == null;
    }
}
