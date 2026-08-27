package com.gym.identity.domain;

import com.gym.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Con người thật — 1 dòng = 1 người.
 *
 * <p>Điểm gốc chống trùng danh tính: {@code phone} là duy nhất nên dù người đó
 * đến từ hotline, tự tải app hay do lễ tân nhập, hệ thống chỉ có một bản ghi.
 */
@Entity
@Table(name = "persons")
@Getter
@Setter
public class Person extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    private LocalDate birthday;

    @Column(name = "national_id", length = 20)
    private String nationalId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String address;

    /** Key trong MinIO (bucket private), KHÔNG phải URL công khai — theo NĐ 13/2023. */
    @Column(name = "photo_key", length = 500)
    private String photoKey;

    @Column(name = "card_uid", length = 64)
    private String cardUid;

    @Column(name = "card_issued_at")
    private OffsetDateTime cardIssuedAt;

    /** Khóa sinh mã QR động, đã mã hóa AES-256-GCM ở tầng ứng dụng. */
    @Column(name = "qr_secret_enc")
    private byte[] qrSecretEnc;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;
}
