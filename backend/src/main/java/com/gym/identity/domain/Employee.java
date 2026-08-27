package com.gym.identity.domain;

import com.gym.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nhân sự, gồm cả huấn luyện viên (gộp trainers + staff + trainer_specialties). */
@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(name = "employee_code", nullable = false, length = 20)
    private String employeeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Department department;

    @Column(length = 80)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @Column(name = "base_salary", precision = 14, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    // ---- Các trường dưới đây chỉ có nghĩa khi department = TRAINING ----

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TrainerLevel level;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String specialties;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "max_members")
    private Integer maxMembers;

    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    public boolean isTrainer() {
        return department == Department.TRAINING;
    }
}
