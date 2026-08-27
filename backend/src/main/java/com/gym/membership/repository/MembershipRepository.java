package com.gym.membership.repository;

import com.gym.membership.domain.Membership;
import com.gym.membership.domain.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByCodeAndDeletedAtIsNull(String code);

    /** Bảng giá công khai — chỉ gói đang bán, theo thứ tự hiển thị đã đặt. */
    List<Membership> findByStatusAndDeletedAtIsNullOrderByDisplayOrderAsc(MembershipStatus status);
}
