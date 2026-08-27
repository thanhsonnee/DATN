package com.gym.identity.domain;

import com.gym.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Hồ sơ hội viên — CHỈ tạo khi chốt mua gói đầu tiên (phương án A).
 *
 * <p>Người tải app mà chưa mua gói không có dòng nào ở bảng này.
 */
@Entity
@Table(name = "members")
@Getter
@Setter
public class Member extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(name = "member_code", nullable = false, length = 20)
    private String memberCode;

    /** Ngày CHỐT MUA gói đầu tiên, không phải ngày tạo tài khoản. */
    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MemberSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by")
    private Member referredBy;

    /** Bệnh nền, chấn thương. PT và chatbot dùng để loại bài tập chống chỉ định. */
    @Column(name = "health_note", columnDefinition = "text")
    private String healthNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MemberGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "last_visit_at")
    private OffsetDateTime lastVisitAt;
}
