package com.gym.identity.repository;

import com.gym.identity.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByPersonIdAndDeletedAtIsNull(Long personId);

    Optional<Member> findByMemberCodeAndDeletedAtIsNull(String memberCode);

    /** Hội viên mới trong khoảng — dùng cho dashboard Admin (join_date, không phải created_at). */
    long countByJoinDateBetweenAndDeletedAtIsNull(LocalDate from, LocalDate to);

    /**
     * Tìm hội viên theo tên, số điện thoại hoặc mã hội viên.
     *
     * <p>Dùng cho ô tìm kiếm gõ-tới-đâu-ra-tới-đó ở màn hình quầy — lễ tân không
     * cần biết trước mã số, chỉ cần gõ vài ký tự tên hoặc số điện thoại.
     */
    @Query("SELECT m FROM Member m WHERE m.deletedAt IS NULL AND ("
         + "LOWER(m.person.fullName) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR m.person.phone LIKE CONCAT('%', :q, '%') "
         + "OR LOWER(m.memberCode) LIKE LOWER(CONCAT('%', :q, '%'))) "
         + "ORDER BY m.person.fullName ASC")
    List<Member> timKiem(@Param("q") String q);
}
