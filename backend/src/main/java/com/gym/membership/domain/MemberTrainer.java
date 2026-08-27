package com.gym.membership.domain;

import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Phân công huấn luyện viên cho hội viên.
 *
 * <p>Lưu ý: tiền công tính cho huấn luyện viên THỰC TẾ dạy buổi đó
 * ({@code ptSessions.trainerId}), không phải người được phân công ở đây.
 */
@Entity
@Table(name = "member_trainers")
@Getter
@Setter
public class MemberTrainer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id", nullable = false)
    private Employee trainer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainerRole role = TrainerRole.PRIMARY;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;
}
