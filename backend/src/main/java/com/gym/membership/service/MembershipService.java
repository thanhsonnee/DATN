package com.gym.membership.service;

import com.gym.common.exception.ApiException;
import com.gym.membership.api.dto.MembershipResponse;
import com.gym.membership.domain.Membership;
import com.gym.membership.domain.MembershipStatus;
import com.gym.membership.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepo;

    /** Bảng giá công khai — chỉ hiện gói đang bán. Gói ARCHIVED bị ẩn nhưng hợp đồng cũ vẫn chạy. */
    @Transactional(readOnly = true)
    public List<MembershipResponse> listOnSale() {
        return membershipRepo
                .findByStatusAndDeletedAtIsNullOrderByDisplayOrderAsc(MembershipStatus.ACTIVE)
                .stream()
                .map(MembershipResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MembershipResponse getById(Long id) {
        return MembershipResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Membership require(Long id) {
        return membershipRepo.findById(id)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói tập"));
    }
}
