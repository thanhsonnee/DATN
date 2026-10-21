package com.gym.training.domain;

import com.gym.identity.domain.Employee;
import com.gym.membership.domain.Registration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Giáo án tập luyện PT soạn cho hội viên.
 *
 * <p>Đơn giản hóa nghiệp vụ G: nội dung là TEXT TỰ DO, không tách bảng bài
 * tập/sets/reps, không AI sinh nháp — PT tự viết, tự chịu trách nhiệm nội dung.
 */
@Entity
@Table(name = "workout_plans")
@Getter
@Setter
public class WorkoutPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id", nullable = false)
    private Employee trainer;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** Mảng ngày (ISO yyyy-MM-dd) hội viên đã đánh dấu tập, lưu dạng chuỗi JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_dates", nullable = false, columnDefinition = "jsonb")
    private String completedDates = "[]";

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;
}
