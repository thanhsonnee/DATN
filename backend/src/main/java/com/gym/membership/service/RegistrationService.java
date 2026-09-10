package com.gym.membership.service;

import com.gym.billing.domain.PaymentMethod;
import com.gym.billing.service.BillingService;
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
import com.gym.sales.service.LeadService;
import com.gym.settings.service.SystemSettingService;
import com.gym.training.service.SessionCreditLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    private final RegistrationRepository registrationRepo;
    private final MemberRepository memberRepo;
    private final PersonRepository personRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final MembershipService membershipService;
    private final CodeGenerator codeGenerator;
    private final SessionCreditLedgerService soCai;
    private final ObjectProvider<BillingService> billingServiceProvider;
    private final ObjectProvider<LeadService> leadServiceProvider;
    private final SystemSettingService settings;

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
        requirePackageOnSale(pkg);

        Member member = memberRepo.findByPersonIdAndDeletedAtIsNull(person.getId())
                .orElseGet(() -> createMember(person));
        requireNotBlacklisted(member);

        BigDecimal discount = validateDiscount(req.discountAmount(), req.discountReason(), pkg.getPrice());
        Registration r = buildRegistration(member, pkg, discount, req.discountReason(), req.note(),
                req.assignedTrainerId(), actorUserId);
        r.setRenewFrom(resolveRenewFrom(req.renewFromRegistrationId(), member));

        r = registrationRepo.save(r);
        log.info("Hợp đồng mới: {} - hội viên {} - gói {} - {} đ",
                r.getRegistrationCode(), member.getMemberCode(), pkg.getCode(), r.getFinalPrice());

        xuatHoaDonChoHopDongMoi(r, actorUserId);
        capNhatLeadThanhCong(person.getId());

        return RegistrationResponse.from(r);
    }

    /**
     * Hợp đồng đang được gia hạn tiếp nối, nếu có khai báo. Bắt buộc phải là hợp
     * đồng CỦA CHÍNH hội viên này và đã có {@code endDate} thật (đã kích hoạt ít
     * nhất một lần) — không tiếp nối được vào một hợp đồng còn đang chờ thanh toán.
     */
    private Registration resolveRenewFrom(Long renewFromRegistrationId, Member member) {
        if (renewFromRegistrationId == null) return null;

        Registration cu = registrationRepo.findById(renewFromRegistrationId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng đang gia hạn"));

        if (!cu.getMember().getId().equals(member.getId())) {
            throw ApiException.forbidden("NOT_YOUR_REGISTRATION",
                    "Đây không phải hợp đồng của bạn, không gia hạn được");
        }
        if (cu.getEndDate() == null) {
            throw ApiException.badRequest("RENEW_SOURCE_NOT_ACTIVATED",
                    "Hợp đồng gốc chưa từng kích hoạt, chưa có ngày hết hạn để nối tiếp");
        }
        return cu;
    }

    /**
     * Đăng ký gói tập tại quầy cho khách vãng lai (A1 + A2 Kênh 1).
     *
     * <p>Tự động tạo hồ sơ con người và hội viên nếu chưa có, lập hợp đồng và có thể
     * thu tiền kích hoạt ngay tại quầy nếu {@code req.payNow() == true}.
     */
    @Transactional
    public RegistrationResponse createAtDesk(Long actorUserId, DeskRegistrationRequest req) {
        // Tìm hoặc tạo Person
        Person person = personRepo.findByPhoneAndDeletedAtIsNull(req.phone().trim())
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setFullName(req.fullName().trim());
                    p.setPhone(req.phone().trim());
                    if (req.email() != null && !req.email().isBlank()) {
                        p.setEmail(req.email().trim());
                    }
                    return personRepo.save(p);
                });

        // Tìm hoặc tạo Member
        Member member = memberRepo.findByPersonIdAndDeletedAtIsNull(person.getId())
                .orElseGet(() -> {
                    Member m = new Member();
                    m.setPerson(person);
                    m.setMemberCode(codeGenerator.nextMemberCode());
                    m.setJoinDate(LocalDate.now());
                    m.setStatus(MemberStatus.ACTIVE);
                    m.setSource(MemberSource.WALK_IN);
                    m = memberRepo.save(m);
                    log.info("Hội viên mới tại quầy: {} - {}", m.getMemberCode(), person.getFullName());
                    return m;
                });

        Membership pkg = membershipService.require(req.membershipId());
        requirePackageOnSale(pkg);
        requireNotBlacklisted(member);

        BigDecimal discount = validateDiscount(req.discountAmount(), req.discountReason(), pkg.getPrice());
        Registration r = buildRegistration(member, pkg, discount, req.discountReason(), req.note(),
                req.assignedTrainerId(), actorUserId);

        r = registrationRepo.save(r);
        log.info("Hợp đồng đăng ký tại quầy: {} - hội viên {} - gói {} - {} đ",
                r.getRegistrationCode(), member.getMemberCode(), pkg.getCode(), r.getFinalPrice());

        xuatHoaDonChoHopDongMoi(r, actorUserId);
        capNhatLeadThanhCong(person.getId());

        if (req.payNow()) {
            PaymentMethod method = req.paymentMethod() != null
                    ? req.paymentMethod()
                    : PaymentMethod.CASH;
            BillingService billing = billingServiceProvider.getIfAvailable();
            if (billing != null) {
                return billing.xacNhanGoiTap(r.getId(), actorUserId, method, null);
            }
        }

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

        LocalDate start = ngayBatDauKichHoat(r);
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

    /**
     * Hợp đồng thường bắt đầu ngay hôm nay — TRỪ KHI đây là hợp đồng gia hạn tiếp
     * nối một hợp đồng khác còn hạn: lúc đó phải bắt đầu ngay sau ngày hợp đồng cũ
     * kết thúc, không phải hôm nay. Mua gia hạn sớm mà vẫn tính từ hôm nay sẽ làm
     * hai hợp đồng chạy chồng lên nhau, lãng phí đúng số ngày còn lại của gói cũ.
     */
    private LocalDate ngayBatDauKichHoat(Registration r) {
        LocalDate homNay = LocalDate.now();
        if (r.getRenewFrom() == null) return homNay;

        LocalDate hetHanCu = r.getRenewFrom().getEndDate();
        LocalDate ngaySauHopDongCu = hetHanCu.plusDays(1);
        return ngaySauHopDongCu.isAfter(homNay) ? ngaySauHopDongCu : homNay;
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

        int freezeMinAdvanceDays = settings.getInt("membership.freeze.min-advance-days", 3);
        long advance = ChronoUnit.DAYS.between(LocalDate.now(), req.fromDate());
        if (advance < freezeMinAdvanceDays) {
            throw ApiException.badRequest("FREEZE_TOO_LATE",
                    "Phải báo trước ít nhất " + freezeMinAdvanceDays + " ngày");
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

    /**
     * Tự động xử lý hết hạn cho các hợp đồng đã qua ngày kết thúc (A5).
     *
     * <p>Chuyển trạng thái ACTIVE → COMPLETED, thu hồi các buổi tập PT chưa dùng vào sổ cái.
     */
    @Transactional
    public int xuLyHetHan() {
        LocalDate today = LocalDate.now();
        List<Registration> expiredList = registrationRepo
                .findByStatusAndDeletedAtIsNullAndEndDateBefore(RegistrationStatus.ACTIVE, today);

        for (Registration r : expiredList) {
            r.setStatus(RegistrationStatus.COMPLETED);
            r.setClosedAt(OffsetDateTime.now());
            r.setCloseReason("EXPIRED");

            // Thu hồi số buổi còn dư trong sổ cái tín dụng
            try {
                soCai.thuHoiBuoiHetHan(r);
            } catch (Exception ex) {
                log.error("Lỗi khi thu hồi buổi hết hạn cho hợp đồng {}: {}", r.getRegistrationCode(), ex.getMessage());
            }

            registrationRepo.save(r);
            log.info("Hợp đồng {} hết hạn: kết thúc vào ngày {}, đã chuyển trạng thái COMPLETED",
                    r.getRegistrationCode(), r.getEndDate());
        }

        return expiredList.size();
    }

    /**
     * Tự động hủy các hợp đồng PENDING_PAYMENT bị bỏ ngang quá 48 giờ (A2).
     */
    @Transactional
    public int huyHopDongBoNgang() {
        LocalDate hanCuoi = LocalDate.now().minusDays(2);
        List<Registration> list = registrationRepo
                .findByStatusAndDeletedAtIsNullAndContractDateBefore(RegistrationStatus.PENDING_PAYMENT, hanCuoi);

        for (Registration r : list) {
            r.setStatus(RegistrationStatus.CANCELLED);
            r.setClosedAt(OffsetDateTime.now());
            r.setCloseReason("UNPAID_TIMEOUT_48H");
            registrationRepo.save(r);
            huyHoaDonNeuCo(r.getId());
            log.info("Hợp đồng {} bị hủy tự động do quá 48h chưa thanh toán", r.getRegistrationCode());
        }

        return list.size();
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

    private void requirePackageOnSale(Membership pkg) {
        if (pkg.getStatus() != MembershipStatus.ACTIVE) {
            throw ApiException.badRequest("PACKAGE_NOT_ON_SALE", "Gói tập này đã ngừng bán");
        }
    }

    private void requireNotBlacklisted(Member member) {
        if (member.getStatus() == MemberStatus.BLACKLISTED) {
            throw ApiException.forbidden("MEMBER_BLACKLISTED",
                    "Hội viên đang trong danh sách hạn chế, không thể mua gói");
        }
    }

    /**
     * Chuẩn hóa về 2 chữ số thập phân: mọi số tiền trả ra API phải cùng một định
     * dạng, tránh chỗ trả 420000 chỗ trả 420000.00 gây khó cho giao diện.
     */
    private BigDecimal validateDiscount(BigDecimal rawDiscount, String discountReason, BigDecimal packagePrice) {
        BigDecimal discount = (rawDiscount == null ? BigDecimal.ZERO : rawDiscount)
                .setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(BigDecimal.ZERO) > 0
                && (discountReason == null || discountReason.isBlank())) {
            throw ApiException.badRequest("DISCOUNT_REASON_REQUIRED",
                    "Có giảm giá thì bắt buộc ghi tên chương trình khuyến mãi");
        }
        if (discount.compareTo(packagePrice) > 0) {
            throw ApiException.badRequest("DISCOUNT_TOO_LARGE",
                    "Số tiền giảm không được vượt quá giá gói");
        }
        return discount;
    }

    /** Dựng hợp đồng mới từ gói đã chọn — dùng chung giữa {@code create} và {@code createAtDesk}. */
    private Registration buildRegistration(Member member, Membership pkg, BigDecimal discount,
                                            String discountReason, String note, Long assignedTrainerId,
                                            Long actorUserId) {
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
        r.setDiscountReason(discount.compareTo(BigDecimal.ZERO) > 0 ? discountReason : null);
        r.setFinalPrice(pkg.getPrice().subtract(discount));

        r.setContractDate(LocalDate.now());
        r.setStatus(RegistrationStatus.PENDING_PAYMENT);
        r.setNote(note);

        if (assignedTrainerId != null) {
            Employee trainer = employeeRepo.findById(assignedTrainerId)
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

        return r;
    }

    /**
     * Phân đoạn F: Tự động cập nhật phễu lead sang WON khi khách chốt hợp đồng.
     *
     * <p>Đây chỉ là tác vụ phụ (ghi sổ CRM) — một lỗi ở đây không được phép làm
     * hỏng giao dịch mua gói/thanh toán chính của khách, nên phải nuốt lỗi và
     * ghi log, giống cách {@link #xuLyHetHan} xử lý việc thu hồi buổi hết hạn.
     */
    private void capNhatLeadThanhCong(Long personId) {
        try {
            leadServiceProvider.ifAvailable(ls -> ls.markWonByPersonId(personId));
        } catch (Exception ex) {
            log.error("Lỗi khi cập nhật lead sang WON cho personId={}: {}", personId, ex.getMessage());
        }
    }

    /**
     * Xuất hóa đơn CHỜ THU ngay khi hợp đồng được lập, để hợp đồng hiện diện
     * trong sổ công nợ của lễ tân (Công nợ) dù chưa ai bấm "Xác nhận gói tập" —
     * trước đây hóa đơn chỉ được tạo lười lúc xác nhận, nên hợp đồng hội viên tự
     * đăng ký trên app hoàn toàn vô hình với danh sách công nợ cho tới lúc đó.
     * Hạn thanh toán khớp mốc tự hủy 48h ở {@link #huyHopDongBoNgang}.
     */
    private void xuatHoaDonChoHopDongMoi(Registration r, Long actorUserId) {
        BillingService billing = billingServiceProvider.getIfAvailable();
        if (billing != null) {
            billing.xuatHoaDon(r.getId(), actorUserId, LocalDate.now().plusDays(2));
        }
    }

    private void huyHoaDonNeuCo(Long registrationId) {
        BillingService billing = billingServiceProvider.getIfAvailable();
        if (billing != null) {
            billing.huyHoaDonTheoHopDong(registrationId);
        }
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
