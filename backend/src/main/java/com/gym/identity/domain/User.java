package com.gym.identity.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gym.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Tài khoản đăng nhập. Một {@link Person} có thể có nhiều tài khoản với vai trò khác nhau.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(nullable = false, length = 100)
    private String username;

    /**
     * BCrypt cost 12. TUYỆT ĐỐI không lộ ra ngoài:
     * không có trong DTO, bị {@code @JsonIgnore} chặn, và không in trong toString.
     */
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_role", nullable = false, length = 20)
    private UserRole primaryRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "locked_reason", length = 255)
    private String lockedReason;

    /** Admin khóa đến ngày này. {@code null} = khóa vô thời hạn. */
    @Column(name = "locked_until")
    private LocalDate lockedUntil;

    @Column(name = "failed_attempts", nullable = false)
    private Short failedAttempts = 0;

    /**
     * HỆ THỐNG tự đặt khi nhập sai mật khẩu quá số lần cho phép.
     * Tách khỏi {@code lockedUntil} để job mở khóa tự động không phá lệnh khóa của Admin.
     */
    @Column(name = "auto_locked_until")
    private OffsetDateTime autoLockedUntil;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** Đăng nhập được không — xét cả khóa thủ công lẫn khóa tự động. */
    public boolean canLogin() {
        if (status == UserStatus.LOCKED) return false;
        return autoLockedUntil == null || autoLockedUntil.isBefore(OffsetDateTime.now());
    }

    @Override
    public String toString() {
        return "User(id=" + getId() + ", username=" + username + ", role=" + primaryRole + ")";
    }
}
