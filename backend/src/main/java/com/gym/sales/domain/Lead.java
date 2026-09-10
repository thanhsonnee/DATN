package com.gym.sales.domain;

import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Person;
import com.gym.membership.domain.Membership;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Khách hàng tiềm năng (Lead) — Nhóm Bán hàng & CRM.
 *
 * <p>Mỗi lead gắn chặt với một {@link Person} thật để chống trùng số điện thoại.
 * Lịch sử tương tác gần nhất lưu trực tiếp tại các trường liên hệ, không dùng bảng phụ.
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private LeadSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interested_membership_id")
    private Membership interestedMembership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private Employee assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private LeadStage stage = LeadStage.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "lost_reason", length = 30)
    private LostReason lostReason;

    @Column(name = "last_contact_at")
    private OffsetDateTime lastContactAt;

    @Column(name = "last_contact_note", columnDefinition = "TEXT")
    private String lastContactNote;

    @Column(name = "next_follow_up")
    private LocalDate nextFollowUp;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
