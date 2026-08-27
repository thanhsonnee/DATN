package com.gym.membership.service;

import com.gym.common.exception.ApiException;
import com.gym.common.util.CodeGenerator;
import com.gym.identity.domain.*;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.api.dto.*;
import com.gym.membership.domain.*;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.training.service.SessionCreditLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Vòng đời hợp đồng: chốt mua → kích hoạt → bảo lưu → kết thúc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    /** Phải báo trước ít nhất ngần này ngày mới được bảo lưu. Sẽ chuyển sang system_settings. */
    private static final int FREEZE_MIN_ADVANCE_DAYS = 3;

    private final RegistrationRepository registrationRepo;
    private final MemberRepository memberRepo;
    private final PersonRepository personRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final MembershipService membershipService;
    private final CodeGenerator codeGenerator;
    private final SessionCreditLedgerService soCai;

    // ------------------------------------------------------------- chốt mua

    /**
     * Khách chốt mua gói.
     *
     * <p>Đây là nơi DUY NHẤT tạo ra hồ sơ hội viên. Tạo ngay lúc chốt mua chứ không
     * chờ trả xong tiền, vì {@code registrations.member_id} là NOT NULL — hợp đồng
     * ở trạng thái chờ thanh toán vẫn phải thuộc về một hội viên có thật.
     */
    @Transactional
    public RegistrationResponse create(Long actorUserId, CreateRegistrationRequest req) {

        Person person = resolveBuyer(actorUserId, req.personId());
        Membership pkg = membershipService.require(req.membershipId());

        if (pkg.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.badRequest("PACKAGE_NOT_ON_SALE", "Gói tập này đã ngừng bán");
        }

        Member member = memberRepo.findByPersonIdAndDeletedAtIsNull(person.getId())
                .orElseGet(() -> createMember(person));

        if (member.getStatus() == MemberStatus.BLACKLISTED) {
            throw ApiException.forbidden("MEMBER_BLACKLISTED",
                    "Hội viên đang trong danh sách hạn chế, không thể mua gói");
        }

        // Chuẩn hóa về 2 chữ số thập phân: mọi số tiền trả ra API phải cùng một
        // định dạng, tránh chỗ trả 420000 chỗ trả 420000.00 gây khó cho giao diện.
        BigDecimal discount = (req.discountAmount() == null ? BigDecimal.ZERO : req.discountAmount())
                .setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(BigDecimal.ZERO) > 0
                && (req.discountReason() == null || req.discountReason().isBlank())) {
            throw ApiException.badRequest("DISCOUNT_REASON_REQUIRED",
                    "Có giảm giá thì bắt buộc ghi tên chương trình khuyến mãi");
        }
        if (discount.compareTo(pkg.getPrice()) > 0) {
            throw ApiException.badRequest("DISCOUNT_TOO_LARGE",
                    "Số tiền giảm không được vượt quá giá gói");
        }

        Registration r = new Registration();
        r.setRegistrationCode(codeGenerator.nextRegistrationCode());
        r.setMember(member);
        r.setMembership(pkg);

        // ---- Sao chép điều khoản LÚC KÝ. Đổi giá gói về sau không làm thay đổi hợp đồng này ----
        r.setPackageType(pkg.getPackageType());
        r.setDurationDays(pkg.getDurationDays());
        r.setSessionsTotal(pkg.getSessionCount());
        r.setListPrice(pkg.getPrice());
        r.setDiscountAmount(discount);
        r.setDiscountReason(discount.compareTo(BigDecimal.ZERO) > 0 ? req.discountReason() : null);
        r.setFinalPrice(pkg.getPrice().subtract(discount));

        r.setContractDate(LocalDate.now());
        r.setStatus(RegistrationStatus.PENDING_PAYMENT);
        r.setNote(req.note());

        if (req.assignedTrainerId() != null) {
            Employee trainer = employeeRepo.findById(req.assignedTrainerId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy huấn luyện viên"));
            if (!trainer.isTrainer()) {
                throw ApiException.badRequest("NOT_A_TRAINER",
                        "Nhân viên được chọn không phải huấn luyện viên");
            }
            r.setAssignedTrainer(trainer);
        }

        // Nhân viên bán hàng: ghi lại để tính hoa hồng. Hội viên tự mua thì để trống.
        User actor = userRepo.findById(actorUserId).orElse(null);
        if (actor != null && actor.getPrimaryRole() != UserRole.MEMBER) {
            employeeRepo.findByPersonIdAndDeletedAtIsNull(actor.getPerson().getId())
                    .ifPresent(r::setSoldBy);
        }

        r = registrationRepo.save(r);
        log.info("Hợp đồng mới: {} - hội viên {} - gói {} - {} đ",
                r.getRegistrationCode(), member.getMemberCode(), pkg.getCode(), r.getFinalPrice());

        return RegistrationResponse.from(r);
    }

    /**
     * Kích hoạt hợp đồng sau khi thu đủ tiền.
     *
     * <p>TẠM THỜI gọi trực tiếp. Khi có module thanh toán, bước này sẽ do sự kiện
     * "khoản thu đã thành công" kích hoạt, không gọi tay nữa.
     */
    @Transactional
    public RegistrationResponse activate(Long registrationId) {

        Registration r = require(registrationId);

        if (r.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("INVALID_STATE",
                    "Chỉ kích hoạt được hợp đồng đang chờ thanh toán. Trạng thái hiện tại: "
                            + r.getStatus());
        }

        LocalDate start = LocalDate.now();
        r.setStartDate(start);
        if (r.getDurationDays() != null) {
            // Ngày bắt đầu tính là ngày đầu tiên, nên trừ đi 1
            r.setEndDate(start.plusDays(r.getDurationDays() - 1L));
        }
        r.setActivatedAt(OffsetDateTime.now());
        r.setStatus(RegistrationStatus.ACTIVE);

        // CẤP BUỔI vào sổ cái, cùng giao dịch với việc kích hoạt: hoặc cả hai
        // cùng thành công, hoặc cả hai cùng quay lui. Không thể có hợp đồng đang
        // chạy mà sổ cái trống, hay ngược lại.
        if (r.getSessionsTotal() != null && r.getSessionsTotal() > 0) {
            soCai.capBuoi(r, r.getSessionsTotal());
        }

        log.info("Kích hoạt hợp đồng {}: {} → {}, cấp {} buổi",
                r.getRegistrationCode(), start, r.getEndDate(), r.getSessionsTotal());
        return RegistrationResponse.from(r);
    }

    // -------------------------------------------------------------- bảo lưu

    /**
     * Hội viên xin bảo lưu. Mỗi hợp đồng chỉ được bảo lưu MỘT LẦN.
     */
    @Transactional
    public RegistrationResponse requestFreeze(Long registrationId, Long actorUserId, FreezeRequest req) {

        Registration r = require(registrationId);

        if (r.getStatus() != RegistrationStatus.ACTIVE) {
            throw ApiException.badRequest("INVALID_STATE",
                    "Chỉ hợp đồng đang hiệu lực mới được bảo lưu");
        }
        if (r.getFreezeStatus() != null) {
            throw ApiException.conflict("ALREADY_FROZEN_ONCE",
                    "Hợp đồng này đã sử dụng lượt bảo lưu, mỗi hợp đồng chỉ được bảo lưu một lần");
        }
        if (req.toDate().isBefore(req.fromDate())) {
            throw ApiException.badRequest("INVALID_RANGE",
                    "Ngày kết thúc phải sau ngày bắt đầu");
        }

        int maxDays = r.getMembership().getMaxFreezeDays();
        if (maxDays <= 0) {
            throw ApiException.badRequest("FREEZE_NOT_ALLOWED",
                    "Gói tập này không cho phép bảo lưu");
        }

        long days = ChronoUnit.DAYS.between(req.fromDate(), req.toDate()) + 1;
        if (days > maxDays) {
            throw ApiException.badRequest("FREEZE_TOO_LONG",
                    "Gói này chỉ cho bảo lưu tối đa " + maxDays + " ngày, bạn đang xin " + days + " ngày");
        }

        long advance = ChronoUnit.DAYS.between(LocalDate.now(), req.fromDate());
        if (advance < FREEZE_MIN_ADVANCE_DAYS) {
            throw ApiException.badRequest("FREEZE_TOO_LATE",
                    "Phải báo trước ít nhất " + FREEZE_MIN_ADVANCE_DAYS + " ngày");
        }
        if (r.getEndDate() != null && !req.fromDate().isBefore(r.getEndDate())) {
            throw ApiException.badRequest("FREEZE_AFTER_EXPIRY",
                    "Ngày bảo lưu phải nằm trong thời hạn hợp đồng");
        }

        r.setFreezeFromDate(req.fromDate());
        r.setFreezeToDate(req.toDate());
        r.setFreezeReason(req.reason());
        r.setFreezeReasonType(req.reasonType() == null
                ? FreezeReasonType.OTHER : FreezeReasonType.valueOf(req.reasonType()));
        r.setFreezeAttachmentKey(req.attachmentKey());
        r.setFreezeStatus(FreezeStatus.PENDING);
        userRepo.findById(actorUserId).ifPresent(r::setFreezeRequestedBy);

        // freeze_days là cột do CSDL tự tính, chỉ có giá trị sau khi lệnh UPDATE
        // thực sự chạy. Không đẩy xuống thì phản hồi trả về số ngày rỗng.
        r = registrationRepo.saveAndFlush(r);

        log.info("Yêu cầu bảo lưu {}: {} → {} ({} ngày)",
                r.getRegistrationCode(), req.fromDate(), req.toDate(), days);

        return RegistrationResponse.from(r);
    }

    /**
     * Duyệt bảo lưu.
     *
     * <p>Khi duyệt, ngày hết hạn được ĐẨY LÙI đúng số ngày bảo lưu — hội viên không
     * mất số ngày đã trả tiền. Trong kỳ bảo lưu, hệ thống cũng dừng ghi nhận doanh thu
     * vì phòng gym không phục vụ ngày nào.
     */
    @Transactional
    public RegistrationResponse approveFreeze(Long registrationId, Long approverUserId, boolean approved,
                                              String rejectReason) {

        Registration r = require(registrationId);

        if (r.getFreezeStatus() != FreezeStatus.PENDING) {
            throw ApiException.badRequest("NO_PENDING_FREEZE",
                    "Hợp đồng này không có yêu cầu bảo lưu nào đang chờ duyệt");
        }

        userRepo.findById(approverUserId).ifPresent(r::setFreezeApprovedBy);

        if (!approved) {
            r.setFreezeStatus(FreezeStatus.REJECTED);
            r.setFreezeReason(r.getFreezeReason() + " | Từ chối: " + rejectReason);
            return RegistrationResponse.from(r);
        }

        int days = r.getFreezeDays() != null ? r.getFreezeDays()
                : (int) (ChronoUnit.DAYS.between(r.getFreezeFromDate(), r.getFreezeToDate()) + 1);

        LocalDate oldEnd = r.getEndDate();
        if (oldEnd != null) {
            r.setEndDate(oldEnd.plusDays(days));
        }

        // Đã tới ngày bắt đầu thì khóa luôn, chưa tới thì chờ job đêm chuyển sang FROZEN
        if (!r.getFreezeFromDate().isAfter(LocalDate.now())) {
            r.setFreezeStatus(FreezeStatus.ACTIVE);
            r.setStatus(RegistrationStatus.FROZEN);
        } else {
            r.setFreezeStatus(FreezeStatus.APPROVED);
        }

        log.info("Duyệt bảo lưu {}: hạn hợp đồng {} → {} (thêm {} ngày)",
                r.getRegistrationCode(), oldEnd, r.getEndDate(), days);

        return RegistrationResponse.from(r);
    }

    // ---------------------------------------------------------------- truy vấn

    @Transactional(readOnly = true)
    public List<RegistrationResponse> myRegistrations(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        return memberRepo.findByPersonIdAndDeletedAtIsNull(user.getPerson().getId())
                .map(m -> registrationRepo
                        .findByMemberIdAndDeletedAtIsNullOrderByContractDateDesc(m.getId())
                        .stream().map(RegistrationResponse::from).toList())
                // Chưa mua gói thì chưa có hợp đồng nào — trả danh sách rỗng, không phải lỗi
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public RegistrationResponse getById(Long id) {
        return RegistrationResponse.from(require(id));
    }

    /**
     * Hợp đồng đã chốt mua nhưng chưa thu tiền.
     *
     * <p>Đây là danh sách lễ tân cần thấy NGAY khi hội viên tự mua gói trên app rồi
     * ra quầy trả tiền — không có màn hình này thì lễ tân không có cách nào biết
     * hợp đồng nào đang chờ, phải hỏi hội viên đọc mã số hoặc tra tay vào CSDL.
     */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> choThanhToan() {
        return registrationRepo
                .findByStatusAndDeletedAtIsNullOrderByContractDateAsc(RegistrationStatus.PENDING_PAYMENT)
                .stream().map(RegistrationResponse::from).toList();
    }

    /** Yêu cầu bảo lưu đang chờ duyệt — để nhân viên thấy và bấm, không cần biết mã số. */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> choDuyetBaoLuu() {
        return registrationRepo
                .findByFreezeStatusAndDeletedAtIsNullOrderByFreezeFromDateAsc(FreezeStatus.PENDING)
                .stream().map(RegistrationResponse::from).toList();
    }

    /** Danh sách sắp hết hạn để Sale gọi mời gia hạn. */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> expiringWithin(int days) {
        LocalDate today = LocalDate.now();
        return registrationRepo.findExpiringBetween(today, today.plusDays(days))
                .stream().map(RegistrationResponse::from).toList();
    }

    // ---------------------------------------------------------------- riêng tư

    private Registration require(Long id) {
        return registrationRepo.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng"));
    }

    /**
     * Xác định hợp đồng này mua cho ai.
     *
     * <p>Hội viên tự mua thì LUÔN là chính họ — bỏ qua {@code personId} client gửi lên,
     * nếu không ai cũng mua được gói dưới tên người khác.
     */
    private Person resolveBuyer(Long actorUserId, Long requestedPersonId) {
        User actor = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        if (actor.getPrimaryRole() == UserRole.MEMBER) {
            return actor.getPerson();
        }
        if (requestedPersonId == null) {
            throw ApiException.badRequest("PERSON_REQUIRED",
                    "Nhân viên tạo hợp đồng phải chọn khách hàng");
        }
        return personRepo.findById(requestedPersonId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    /** Tạo hồ sơ hội viên — chỉ xảy ra một lần, lúc chốt mua gói đầu tiên. */
    private Member createMember(Person person) {
        Member m = new Member();
        m.setPerson(person);
        m.setMemberCode(codeGenerator.nextMemberCode());
        m.setJoinDate(LocalDate.now());
        m.setStatus(MemberStatus.ACTIVE);
        m.setSource(MemberSource.APP_SELF);
        m = memberRepo.save(m);
        log.info("Hội viên mới: {} - {}", m.getMemberCode(), person.getFullName());
        return m;
    }
}
