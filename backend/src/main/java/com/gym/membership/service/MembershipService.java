package com.gym.membership.service;

import com.gym.common.exception.ApiException;
import com.gym.membership.api.dto.CreateMembershipRequest;
import com.gym.membership.api.dto.MembershipAdminResponse;
import com.gym.membership.api.dto.MembershipResponse;
import com.gym.membership.api.dto.UpdateMembershipRequest;
import com.gym.membership.domain.Membership;
import com.gym.membership.domain.MembershipStatus;
import com.gym.membership.domain.PackageType;
import com.gym.membership.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
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

    /** Quản lý — Admin thấy mọi gói (kể cả ARCHIVED), không chỉ gói đang bán. */
    @Transactional(readOnly = true)
    public List<MembershipAdminResponse> listAllForAdmin() {
        return membershipRepo.findByDeletedAtIsNullOrderByDisplayOrderAsc()
                .stream()
                .map(MembershipAdminResponse::from)
                .toList();
    }

    @Transactional
    public MembershipAdminResponse create(CreateMembershipRequest req) {
        membershipRepo.findByCodeAndDeletedAtIsNull(req.code()).ifPresent(m -> {
            throw ApiException.conflict("CODE_TAKEN", "Mã gói \"" + req.code() + "\" đã tồn tại");
        });
        validateBusinessRules(req.packageType(), req.durationDays(), req.sessionCount(), req.ptValueRatio());

        Membership m = new Membership();
        m.setCode(req.code().trim());
        m.setName(req.name().trim());
        m.setPackageType(req.packageType());
        m.setDurationDays(req.durationDays());
        m.setSessionCount(req.sessionCount());
        m.setPrice(req.price());
        m.setIncludesTrainer(req.includesTrainer());
        m.setPtValueRatio(req.ptValueRatio());
        m.setMaxFreezeDays(req.maxFreezeDays());
        m.setIsRefundable(req.isRefundable());
        m.setDescription(req.description() != null ? req.description().trim() : null);
        m.setDisplayOrder(req.displayOrder());
        m.setStatus(MembershipStatus.ACTIVE);

        Membership saved = membershipRepo.save(m);
        log.info("Đã tạo gói tập {}: {}", saved.getCode(), saved.getName());
        return MembershipAdminResponse.from(saved);
    }

    @Transactional
    public MembershipAdminResponse update(Long id, UpdateMembershipRequest req) {
        Membership m = require(id);

        membershipRepo.findByCodeAndDeletedAtIsNull(req.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("CODE_TAKEN", "Mã gói \"" + req.code() + "\" đã tồn tại");
                });
        validateBusinessRules(req.packageType(), req.durationDays(), req.sessionCount(), req.ptValueRatio());

        m.setCode(req.code().trim());
        m.setName(req.name().trim());
        m.setPackageType(req.packageType());
        m.setDurationDays(req.durationDays());
        m.setSessionCount(req.sessionCount());
        m.setPrice(req.price());
        m.setIncludesTrainer(req.includesTrainer());
        m.setPtValueRatio(req.ptValueRatio());
        m.setMaxFreezeDays(req.maxFreezeDays());
        m.setIsRefundable(req.isRefundable());
        m.setDescription(req.description() != null ? req.description().trim() : null);
        m.setDisplayOrder(req.displayOrder());
        m.setStatus(req.status());

        Membership saved = membershipRepo.save(m);
        log.info("Đã cập nhật gói tập {}: {}", saved.getCode(), saved.getName());
        return MembershipAdminResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Membership m = require(id);
        m.setDeletedAt(OffsetDateTime.now());
        membershipRepo.save(m);
        log.info("Đã xóa gói tập {}: {}", m.getCode(), m.getName());
    }

    /** Khớp đúng các CHECK constraint ở V3__membership.sql — validate sớm ở đây để trả lỗi rõ ràng
     *  thay vì để lộ lỗi ràng buộc SQL thô khi save() thất bại. */
    private void validateBusinessRules(PackageType type, Integer durationDays, Integer sessionCount,
                                        BigDecimal ptValueRatio) {
        if (type == PackageType.HYBRID) {
            if (ptValueRatio == null
                    || ptValueRatio.compareTo(BigDecimal.ZERO) <= 0
                    || ptValueRatio.compareTo(BigDecimal.ONE) >= 0) {
                throw ApiException.badRequest("PT_RATIO_REQUIRED",
                        "Gói kết hợp (HYBRID) bắt buộc nhập tỷ lệ giá trị PT trong khoảng (0, 1)");
            }
        }
        if ((type == PackageType.SESSION_BASED || type == PackageType.HYBRID)
                && (sessionCount == null || sessionCount <= 0)) {
            throw ApiException.badRequest("SESSION_COUNT_REQUIRED",
                    "Gói tính theo buổi bắt buộc nhập số buổi lớn hơn 0");
        }
        if ((type == PackageType.TIME_BASED || type == PackageType.HYBRID || type == PackageType.DAY_PASS)
                && (durationDays == null || durationDays <= 0)) {
            throw ApiException.badRequest("DURATION_REQUIRED",
                    "Gói tính theo thời hạn bắt buộc nhập số ngày lớn hơn 0");
        }
    }
}
