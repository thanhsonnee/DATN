package com.gym.training.domain;

import com.gym.identity.domain.User;
import com.gym.membership.domain.Registration;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;

/**
 * Một bút toán trong SỔ CÁI TÍN DỤNG BUỔI TẬP.
 *
 * <p><b>Bảng này CHỈ ĐƯỢC GHI THÊM.</b> Trigger ở tầng cơ sở dữ liệu chặn mọi
 * lệnh sửa và xóa, nên entity cố tình không có phương thức nào cho phép thay đổi
 * bút toán đã ghi. Muốn sửa sai thì ghi một bút toán {@code ADJUST} mới — vết cũ
 * được giữ nguyên để truy vết.
 *
 * <p>Số dư buổi tập không phải một cột bị ghi đè, mà là <b>tổng cộng dồn</b> của
 * mọi bút toán. Cột {@code balanceAfter} lưu lại số dư tại từng thời điểm để đối
 * chiếu chéo với tổng, nhờ vậy phát hiện sai sót ngay mà không phải tính lại
 * toàn bộ lịch sử.
 */
@Entity
@Table(name = "session_credit_ledger")
@Getter
@Setter
public class SessionCreditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    /** Dương là cộng buổi, âm là trừ buổi. */
    @Column(nullable = false)
    private Integer delta;

    /** Số dư SAU khi áp bút toán này. */
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private LedgerSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;
}
