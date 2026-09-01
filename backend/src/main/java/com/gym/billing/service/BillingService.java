package com.gym.billing.service;

import com.gym.billing.api.dto.CashShiftResponse;
import com.gym.billing.api.dto.InvoiceResponse;
import com.gym.billing.api.dto.PaymentResponse;
import com.gym.billing.domain.*;
import com.gym.billing.repository.CashShiftRepository;
import com.gym.billing.repository.InvoiceRepository;
import com.gym.billing.repository.PaymentRepository;
import com.gym.common.exception.ApiException;
import com.gym.common.util.CodeGenerator;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.User;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.api.dto.RegistrationResponse;
import com.gym.membership.domain.Registration;
import com.gym.membership.domain.RegistrationStatus;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.membership.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Hóa đơn, thu tiền và đối soát tiền mặt theo ca.
 *
 * <h3>Điểm nối quan trọng của hệ thống</h3>
 * Khi hóa đơn được thu đủ, dịch vụ này <b>tự kích hoạt hợp đồng</b>, và việc kích
 * hoạt lại tự cấp buổi vào sổ cái. Cả ba việc nằm trong <b>một giao dịch</b>: hoặc
 * cùng thành công, hoặc cùng quay lui. Không thể có chuyện khách trả tiền xong mà
 * hợp đồng chưa chạy, hay hợp đồng chạy mà sổ cái trống.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    // Trả DTO chứ không trả entity: dữ liệu liên kết chỉ nạp được khi còn trong
    // giao dịch, mà controller thì chạy sau khi giao dịch đã đóng.

    private final InvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;
    private final CashShiftRepository shiftRepo;
    private final RegistrationRepository registrationRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final RegistrationService registrationService;
    private final RevenueRecognitionService revenueRecognitionService;
    private final CodeGenerator codeGenerator;

    // ---------------------------------------------------------------- hóa đơn

    /**
     * Xuất hóa đơn cho một hợp đồng.
     *
     * <p>Số tiền sao chép từ {@code registration.finalPrice} đúng một lần tại đây.
     * Mỗi hợp đồng chỉ có một hóa đơn — mua hai gói thì tạo hai hợp đồng riêng.
     */
    @Transactional
    public InvoiceResponse xuatHoaDon(Long registrationId, Long actorUserId, LocalDate hanThanhToan) {

        Registration r = registrationRepo.findById(registrationId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng"));

        invoiceRepo.findByRegistrationIdAndDeletedAtIsNull(registrationId).ifPresent(inv -> {
            throw ApiException.conflict("INVOICE_EXISTS",
                    "Hợp đồng này đã có hóa đơn " + inv.getInvoiceNo());
        });

        Invoice inv = new Invoice();
        inv.setInvoiceNo(codeGenerator.nextInvoiceNo());
        inv.setMember(r.getMember());
        inv.setRegistration(r);
        inv.setDescription(r.getMembership().getName());
        inv.setTotalAmount(r.getFinalPrice());
        inv.setStatus(InvoiceStatus.UNPAID);
        inv.setDueDate(hanThanhToan);
        userRepo.findById(actorUserId).ifPresent(inv::setIssuedBy);

        inv = invoiceRepo.save(inv);
        log.info("Xuất hóa đơn {} cho hợp đồng {}: {} đ",
                inv.getInvoiceNo(), r.getRegistrationCode(), inv.getTotalAmount());
        return InvoiceResponse.from(inv);
    }

    /**
     * Xác nhận gói tập — thao tác MỘT LẦN BẤM cho lễ tân, gộp xuất hóa đơn và
     * thu đủ tiền vào cùng một giao dịch.
     *
     * <p>"Hóa đơn" vẫn tồn tại bên dưới (cần cho sổ công nợ và hoàn tiền của kế
     * toán), nhưng lễ tân không cần biết khái niệm đó — chỉ cần bấm xác nhận
     * sau khi đã cầm tiền hoặc thấy tiền về tài khoản.
     *
     * <p><b>Idempotent theo hợp đồng:</b> hợp đồng có thể đã có hóa đơn từ trước
     * (vd. kế toán xuất hóa đơn công nợ riêng qua {@link #xuatHoaDon}) — trường
     * hợp đó bỏ qua bước tạo mới, thu thẳng vào hóa đơn đã có. {@link #thuTien}
     * tự chặn và báo lỗi rõ ràng nếu hóa đơn đó đã thu đủ hoặc đã bị hủy.
     */
    @Transactional
    public RegistrationResponse xacNhanGoiTap(Long registrationId, Long actorUserId,
                                              PaymentMethod hinhThuc) {
        Invoice inv = invoiceRepo.findByRegistrationIdAndDeletedAtIsNull(registrationId).orElse(null);

        Long invoiceId;
        BigDecimal soTien;
        if (inv == null) {
            InvoiceResponse created = xuatHoaDon(registrationId, actorUserId, LocalDate.now());
            invoiceId = created.id();
            soTien = created.totalAmount();
        } else {
            invoiceId = inv.getId();
            soTien = inv.getTotalAmount().subtract(inv.getPaidAmount());
        }

        thuTien(invoiceId, soTien, hinhThuc, actorUserId, null);
        return registrationService.getById(registrationId);
    }

    // ---------------------------------------------------------------- thu tiền

    /**
     * Ghi nhận một khoản thu.
     *
     * <p>Thu tiền mặt bắt buộc có ca làm việc đang mở — nếu không, tiền sẽ nằm
     * ngoài mọi lần đối soát. Chuyển khoản không cần vì có sao kê ngân hàng.
     *
     * <p>Khi hóa đơn được thu đủ, hợp đồng tự kích hoạt và sổ cái tự được cấp buổi.
     */
    @Transactional
    public PaymentResponse thuTien(Long invoiceId, BigDecimal soTien, PaymentMethod hinhThuc,
                           Long actorUserId, String noiDungChuyenKhoan) {

        Invoice inv = invoiceRepo.findById(invoiceId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn"));

        if (inv.getStatus() == InvoiceStatus.PAID) {
            throw ApiException.conflict("ALREADY_PAID", "Hóa đơn này đã thanh toán đủ");
        }
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw ApiException.badRequest("INVOICE_CANCELLED", "Hóa đơn đã bị hủy");
        }
        if (soTien == null || soTien.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Số tiền thu phải lớn hơn 0");
        }

        BigDecimal tien = soTien.setScale(2, RoundingMode.HALF_UP);
        BigDecimal conNo = inv.getTotalAmount().subtract(inv.getPaidAmount());
        if (tien.compareTo(conNo) > 0) {
            throw ApiException.badRequest("OVERPAY",
                    "Số tiền thu vượt quá số còn nợ. Còn nợ " + conNo + " đ");
        }

        User actor = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        Payment p = new Payment();
        p.setPaymentNo(codeGenerator.nextPaymentNo());
        p.setMember(inv.getMember());
        p.setInvoice(inv);
        p.setPaymentType(PaymentType.PAYMENT);
        p.setMethod(hinhThuc);
        p.setAmount(tien);
        p.setStatus(PaymentStatus.SUCCEEDED);
        p.setPaidAt(OffsetDateTime.now());
        p.setTransferContent(noiDungChuyenKhoan);
        p.setReceivedBy(actor);

        // Tiền mặt PHẢI gắn ca đang mở, nếu không sẽ lọt ngoài đối soát
        if (hinhThuc == PaymentMethod.CASH) {
            p.setCashShift(caDangMoCuaNhanVien(actor));
        }

        p = paymentRepo.save(p);
        capNhatHoaDonSauKhiThu(inv);

        log.info("Thu {} đ cho hóa đơn {} bằng {}", tien, inv.getInvoiceNo(), hinhThuc);
        return PaymentResponse.from(p);
    }

    /**
     * Cập nhật hóa đơn sau mỗi khoản thu, và kích hoạt hợp đồng khi đã thu đủ.
     *
     * <p>Tính lại tổng đã thu từ danh sách khoản thu chứ không cộng dồn vào cột
     * cũ: cách này tự sửa được nếu có khoản thu bị bổ sung hoặc hoàn lại về sau.
     */
    private void capNhatHoaDonSauKhiThu(Invoice inv) {
        BigDecimal daThu = paymentRepo.tongDaThu(inv.getId(), PaymentStatus.SUCCEEDED);
        inv.setPaidAmount(daThu.setScale(2, RoundingMode.HALF_UP));

        if (inv.daThuDu()) {
            inv.setStatus(InvoiceStatus.PAID);
            inv.setPaidAt(OffsetDateTime.now());

            // ĐIỂM NỐI: thu đủ tiền thì hợp đồng chạy, và việc kích hoạt sẽ tự
            // cấp buổi vào sổ cái. Cùng một giao dịch nên không thể lệch nhau.
            Registration r = inv.getRegistration();
            if (r.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
                registrationService.activate(r.getId());
                log.info("Thu đủ hóa đơn {} → kích hoạt hợp đồng {}",
                        inv.getInvoiceNo(), r.getRegistrationCode());
            }
            // E3: Tự động phân bổ doanh thu theo chuẩn kế toán dồn tích
            revenueRecognitionService.generateSchedule(r, inv);
        } else {
            inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
    }

    /**
     * Hoàn tiền — ghi bút toán ÂM trỏ về khoản thu gốc.
     *
     * <p>Bắt buộc có người duyệt và lý do: đây là thao tác làm tiền rời khỏi
     * phòng gym, phải truy được trách nhiệm.
     */
    @Transactional
    public PaymentResponse hoanTien(Long paymentGocId, BigDecimal soTienHoan, String lyDo,
                            Long nguoiDuyetId) {

        Payment goc = paymentRepo.findById(paymentGocId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản thu gốc"));

        if (goc.getPaymentType() != PaymentType.PAYMENT) {
            throw ApiException.badRequest("NOT_A_PAYMENT", "Chỉ hoàn được khoản đã thu");
        }
        if (goc.getStatus() != PaymentStatus.SUCCEEDED) {
            throw ApiException.badRequest("NOT_SUCCEEDED",
                    "Khoản thu gốc chưa thành công, không có gì để hoàn");
        }
        if (lyDo == null || lyDo.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Hoàn tiền bắt buộc ghi lý do");
        }
        if (soTienHoan.compareTo(goc.getAmount()) > 0) {
            throw ApiException.badRequest("REFUND_TOO_LARGE",
                    "Số tiền hoàn không được vượt quá khoản đã thu");
        }

        User nguoiDuyet = userRepo.findById(nguoiDuyetId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người duyệt"));

        Payment p = new Payment();
        p.setPaymentNo(codeGenerator.nextPaymentNo());
        p.setMember(goc.getMember());
        p.setInvoice(goc.getInvoice());
        p.setPaymentType(PaymentType.REFUND);
        p.setMethod(goc.getMethod());
        // ÂM: cộng dồn ra đúng số tiền phòng gym thực sự giữ lại
        p.setAmount(soTienHoan.setScale(2, RoundingMode.HALF_UP).negate());
        p.setStatus(PaymentStatus.SUCCEEDED);
        p.setPaidAt(OffsetDateTime.now());
        p.setRefundOfPayment(goc);
        p.setRefundReason(lyDo);
        p.setApprovedBy(nguoiDuyet);

        // Tiền mặt chi ra từ KÉT CỦA QUẦY, nên gắn vào ca đang trực — không phải
        // ca của người duyệt. Kế toán duyệt nhưng không giữ tiền mặt.
        if (goc.getMethod() == PaymentMethod.CASH) {
            p.setCashShift(caDangTrucQuay(nguoiDuyet));
        }

        p = paymentRepo.save(p);

        Invoice inv = goc.getInvoice();
        inv.setPaidAmount(paymentRepo.tongDaThu(inv.getId(), PaymentStatus.SUCCEEDED)
                .setScale(2, RoundingMode.HALF_UP));
        inv.setStatus(InvoiceStatus.REFUNDED);

        log.warn("Hoàn {} đ cho khoản thu {} — lý do: {}",
                soTienHoan, goc.getPaymentNo(), lyDo);
        return PaymentResponse.from(p);
    }

    // ------------------------------------------------------------ ca làm việc

    /** Mở ca. Mỗi lễ tân chỉ được mở tối đa một ca cùng lúc. */
    @Transactional
    public CashShiftResponse moCa(Long actorUserId, BigDecimal tienDauCa) {
        Employee nv = nhanVienCuaTaiKhoan(actorUserId);

        shiftRepo.findByEmployeeIdAndStatus(nv.getId(), CashShiftStatus.OPEN).ifPresent(ca -> {
            throw ApiException.conflict("SHIFT_ALREADY_OPEN",
                    "Bạn đang có ca mở từ " + ca.getOpenedAt() + ", phải đóng ca cũ trước");
        });

        CashShift ca = new CashShift();
        ca.setEmployee(nv);
        ca.setOpeningBalance(tienDauCa == null ? BigDecimal.ZERO
                : tienDauCa.setScale(2, RoundingMode.HALF_UP));
        ca.setStatus(CashShiftStatus.OPEN);

        ca = shiftRepo.save(ca);
        log.info("Mở ca #{} cho {} với {} đ đầu ca",
                ca.getId(), nv.getEmployeeCode(), ca.getOpeningBalance());
        return CashShiftResponse.from(ca);
    }

    /**
     * Đóng ca và đối soát tiền mặt.
     *
     * <p>Hệ thống tính ra số tiền <b>phải có</b> trong két, lễ tân nhập số
     * <b>đếm được</b>. Lệch nhau thì ca chuyển sang trạng thái cần giải trình —
     * đây là điểm kiểm soát thất thoát chính của quầy.
     */
    @Transactional
    public CashShiftResponse dongCa(Long actorUserId, BigDecimal tienDemDuoc, String lyDoLech) {
        Employee nv = nhanVienCuaTaiKhoan(actorUserId);

        CashShift ca = shiftRepo.findByEmployeeIdAndStatus(nv.getId(), CashShiftStatus.OPEN)
                .orElseThrow(() -> ApiException.badRequest("NO_OPEN_SHIFT",
                        "Bạn không có ca nào đang mở"));

        if (tienDemDuoc == null || tienDemDuoc.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Số tiền đếm được không hợp lệ");
        }

        BigDecimal thuTrongCa = paymentRepo.tongTienMatTrongCa(ca.getId());
        BigDecimal phaiCo = ca.getOpeningBalance().add(thuTrongCa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal demDuoc = tienDemDuoc.setScale(2, RoundingMode.HALF_UP);

        ca.setExpectedCash(phaiCo);
        ca.setCountedCash(demDuoc);
        ca.setClosedAt(OffsetDateTime.now());

        boolean khop = demDuoc.compareTo(phaiCo) == 0;
        if (khop) {
            ca.setStatus(CashShiftStatus.CLOSED);
        } else {
            if (lyDoLech == null || lyDoLech.isBlank()) {
                throw ApiException.badRequest("REASON_REQUIRED",
                        "Tiền đếm được lệch " + demDuoc.subtract(phaiCo)
                                + " đ so với sổ, bắt buộc ghi lý do");
            }
            ca.setStatus(CashShiftStatus.DISCREPANCY);
            ca.setDifferenceReason(lyDoLech);
            log.warn("Ca #{} LỆCH TIỀN: sổ {} đ, đếm được {} đ, lý do: {}",
                    ca.getId(), phaiCo, demDuoc, lyDoLech);
        }

        // Cột difference do CSDL tự tính, chỉ có giá trị sau khi lệnh UPDATE
        // thực sự chạy. Không đẩy xuống thì phản hồi trả về chênh lệch rỗng.
        ca = shiftRepo.saveAndFlush(ca);

        log.info("Đóng ca #{}: thu trong ca {} đ, {}",
                ca.getId(), thuTrongCa, khop ? "khớp sổ" : "LỆCH");
        return CashShiftResponse.from(ca);
    }

    // ---------------------------------------------------------------- truy vấn

    @Transactional(readOnly = true)
    public List<InvoiceResponse> hoaDonCuaHoiVien(Long memberId) {
        return invoiceRepo.findByMemberIdAndDeletedAtIsNullOrderByIssuedAtDesc(memberId)
                .stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> congNo() {
        return invoiceRepo.findByStatusInAndDeletedAtIsNullOrderByDueDateAsc(
                        List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID,
                                InvoiceStatus.OVERDUE))
                .stream().map(InvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse chiTietHoaDon(Long id) {
        return InvoiceResponse.from(invoiceRepo.findById(id)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hóa đơn")));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> khoanThuCuaHoaDon(Long invoiceId) {
        return paymentRepo.findByInvoiceIdOrderByIdAsc(invoiceId)
                .stream().map(PaymentResponse::from).toList();
    }

    /** Ca đang mở của người đang đăng nhập. Trả {@code null} nếu chưa mở ca nào. */
    @Transactional(readOnly = true)
    public CashShiftResponse caHienTai(Long actorUserId) {
        Employee nv = nhanVienCuaTaiKhoan(actorUserId);
        return shiftRepo.findByEmployeeIdAndStatus(nv.getId(), CashShiftStatus.OPEN)
                .map(ca -> {
                    BigDecimal thuTrongCa = paymentRepo.tongTienMatTrongCa(ca.getId());
                    BigDecimal expected = ca.getOpeningBalance().add(thuTrongCa).setScale(2, RoundingMode.HALF_UP);
                    return new CashShiftResponse(
                            ca.getId(),
                            ca.getEmployee().getId(),
                            ca.getEmployee().getPerson().getFullName(),
                            ca.getOpenedAt(),
                            ca.getClosedAt(),
                            ca.getOpeningBalance(),
                            expected,
                            ca.getCountedCash(),
                            ca.getDifference(),
                            ca.getDifferenceReason(),
                            ca.getStatus().name()
                    );
                }).orElse(null);
    }

    // ---------------------------------------------------------------- riêng tư

    private Employee nhanVienCuaTaiKhoan(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .orElseThrow(() -> ApiException.forbidden("NOT_AN_EMPLOYEE",
                        "Tài khoản này không phải nhân viên"));
    }

    /**
     * Ca đang trực quầy, dùng khi chi tiền mặt ra khỏi két.
     *
     * <p>Ưu tiên ca của chính người thao tác. Nếu người đó không trực quầy
     * (ví dụ kế toán duyệt hoàn tiền) thì lấy ca duy nhất đang mở. Có nhiều ca
     * mở cùng lúc thì không đoán được tiền ra từ két nào, phải báo lỗi.
     */
    private CashShift caDangTrucQuay(User actor) {
        var caCuaMinh = employeeRepo.findByPersonIdAndDeletedAtIsNull(actor.getPerson().getId())
                .flatMap(nv -> shiftRepo.findByEmployeeIdAndStatus(nv.getId(), CashShiftStatus.OPEN));
        if (caCuaMinh.isPresent()) return caCuaMinh.get();

        List<CashShift> dangMo = shiftRepo.findByStatusOrderByOpenedAtDesc(CashShiftStatus.OPEN);
        if (dangMo.isEmpty()) {
            throw ApiException.badRequest("NO_OPEN_SHIFT",
                    "Không có ca làm việc nào đang mở, không chi được tiền mặt");
        }
        if (dangMo.size() > 1) {
            throw ApiException.badRequest("MULTIPLE_OPEN_SHIFTS",
                    "Đang có nhiều ca mở cùng lúc, phải chỉ rõ chi tiền từ ca nào");
        }
        return dangMo.get(0);
    }

    private CashShift caDangMoCuaNhanVien(User actor) {
        Employee nv = employeeRepo.findByPersonIdAndDeletedAtIsNull(actor.getPerson().getId())
                .orElseThrow(() -> ApiException.forbidden("NOT_AN_EMPLOYEE",
                        "Chỉ nhân viên mới thu được tiền mặt"));

        return shiftRepo.findByEmployeeIdAndStatus(nv.getId(), CashShiftStatus.OPEN)
                .orElseThrow(() -> ApiException.badRequest("NO_OPEN_SHIFT",
                        "Phải mở ca làm việc trước khi thu tiền mặt, "
                                + "nếu không khoản thu sẽ nằm ngoài đối soát"));
    }
}
