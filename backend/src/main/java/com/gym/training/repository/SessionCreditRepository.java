package com.gym.training.repository;

import com.gym.training.domain.LedgerEntryType;
import com.gym.training.domain.LedgerSourceType;
import com.gym.training.domain.SessionCreditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionCreditRepository extends JpaRepository<SessionCreditEntry, Long> {

    /**
     * Bút toán mới nhất của hợp đồng.
     *
     * <p>Cố tình KHÔNG khóa dòng ở đây. Khóa bút toán cuối là một cái bẫy: lệnh
     * khóa chỉ giữ dòng đã tồn tại, không ngăn được việc chèn dòng mới, nên hai
     * luồng vẫn cùng đọc ra một số dư cũ. Việc tuần tự hóa do khóa dòng HỢP ĐỒNG
     * đảm nhiệm — xem {@code RegistrationRepository.khoaHopDong}.
     */
    @Query("SELECT e FROM SessionCreditEntry e "
         + "WHERE e.registration.id = :registrationId "
         + "ORDER BY e.id DESC LIMIT 1")
    Optional<SessionCreditEntry> layButToanCuoi(@Param("registrationId") Long registrationId);

    /** Toàn bộ lịch sử biến động của một hợp đồng, theo đúng thứ tự phát sinh. */
    List<SessionCreditEntry> findByRegistrationIdOrderByIdAsc(Long registrationId);

    boolean existsByEntryTypeAndSourceTypeAndSourceId(
            LedgerEntryType entryType, LedgerSourceType sourceType, Long sourceId);

    /** Tổng cộng dồn — dùng để đối chiếu với {@code balanceAfter} mới nhất. */
    @Query("SELECT COALESCE(SUM(e.delta), 0) FROM SessionCreditEntry e "
         + "WHERE e.registration.id = :registrationId")
    int tongDelta(@Param("registrationId") Long registrationId);
}
