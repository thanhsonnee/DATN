package com.gym.membership.repository;

import com.gym.membership.domain.Registration;
import com.gym.membership.domain.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByRegistrationCodeAndDeletedAtIsNull(String code);

    /**
     * Khóa dòng hợp đồng để tuần tự hóa mọi lần ghi sổ cái của hợp đồng đó.
     *
     * <p><b>Vì sao khóa hợp đồng chứ không khóa bút toán cuối:</b> lệnh khóa của
     * PostgreSQL chỉ giữ những dòng ĐÃ TỒN TẠI mà truy vấn trả về, không ngăn
     * được việc chèn dòng mới. Hai luồng cùng khóa bút toán cuối sẽ lần lượt đọc
     * ra cùng một số dư cũ — luồng sau không nhìn thấy bút toán mà luồng trước
     * vừa chèn, nên cả hai cùng ghi ra một số dư giống nhau và sổ cái sai.
     *
     * <p>Hợp đồng là dòng cố định, luôn tồn tại từ trước, nên khóa nó buộc mọi
     * luồng phải xếp hàng đúng thứ tự.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Registration r WHERE r.id = :id")
    Optional<Registration> khoaHopDong(@Param("id") Long id);

    List<Registration> findByMemberIdAndDeletedAtIsNullOrderByContractDateDesc(Long memberId);

    /** Hợp đồng đã chốt mua nhưng chưa thu tiền — danh sách để lễ tân xuất hóa đơn. */
    List<Registration> findByStatusAndDeletedAtIsNullOrderByContractDateAsc(RegistrationStatus status);

    /** Yêu cầu bảo lưu đang chờ duyệt — danh sách để nhân viên duyệt, không cần biết mã số. */
    List<Registration> findByFreezeStatusAndDeletedAtIsNullOrderByFreezeFromDateAsc(
            com.gym.membership.domain.FreezeStatus freezeStatus);

    /**
     * Hội viên có đang có hợp đồng hiệu lực không.
     *
     * <p>Đây là câu trả lời cho "còn gói hay hết gói" — thông tin này KHÔNG lưu ở
     * {@code members.status} vì là dữ liệu suy ra được (quyết định 14).
     */
    boolean existsByMemberIdAndStatusAndDeletedAtIsNull(Long memberId, RegistrationStatus status);

    /** Danh sách hợp đồng ACTIVE đã quá ngày hết hạn để xử lý A5. */
    List<Registration> findByStatusAndDeletedAtIsNullAndEndDateBefore(
            RegistrationStatus status, LocalDate date);

    /** Danh sách hợp đồng PENDING_PAYMENT bị bỏ ngang quá 48h để tự động hủy (A2). */
    List<Registration> findByStatusAndDeletedAtIsNullAndContractDateBefore(
            RegistrationStatus status, LocalDate date);

    /** Danh sách sắp hết hạn, để Sale mời gia hạn. */
    @Query("SELECT r FROM Registration r "
         + "WHERE r.status = com.gym.membership.domain.RegistrationStatus.ACTIVE "
         + "  AND r.deletedAt IS NULL "
         + "  AND r.endDate BETWEEN :from AND :to "
         + "ORDER BY r.endDate ASC")
    List<Registration> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Dùng cho dashboard Admin: đếm hợp đồng theo trạng thái (vd. FROZEN). */
    long countByStatusAndDeletedAtIsNull(RegistrationStatus status);

    /** Số hội viên PHÂN BIỆT đang có hợp đồng hiệu lực — "hội viên đang hoạt động" thật sự. */
    @Query("SELECT COUNT(DISTINCT r.member.id) FROM Registration r "
         + "WHERE r.status = com.gym.membership.domain.RegistrationStatus.ACTIVE "
         + "  AND r.deletedAt IS NULL")
    long countDistinctActiveMembers();
}
