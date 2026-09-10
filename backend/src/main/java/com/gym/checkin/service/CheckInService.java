package com.gym.checkin.service;

import com.gym.billing.domain.InvoiceStatus;
import com.gym.billing.repository.InvoiceRepository;
import com.gym.checkin.api.dto.CheckInPreviewResponse;
import com.gym.checkin.api.dto.CheckInResponse;
import com.gym.checkin.domain.*;
import com.gym.checkin.repository.CheckInRepository;
import com.gym.common.exception.ApiException;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.MemberStatus;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.domain.Registration;
import com.gym.membership.domain.RegistrationStatus;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.settings.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kiểm soát ra vào phòng tập.
 *
 * <h3>Luồng đã chốt (Hướng A)</h3>
 * Hội viên mở ứng dụng hiện mã QR → lễ tân quét bằng máy quầy → hệ thống kiểm
 * tra hợp đồng và trả về ảnh hồ sơ → <b>lễ tân nhìn, đối chiếu người thật</b>,
 * rồi quyết định cho vào. Máy không tự mở cửa — người vẫn là chốt chặn cuối.
 *
 * <h3>Vì sao ghi cả lượt bị từ chối</h3>
 * Chỉ ghi lượt vào được thì không biết đã chặn được bao nhiêu ca gian lận — mà
 * đó mới là con số đo hiệu quả của cơ chế kiểm soát.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private final CheckInRepository checkInRepo;
    private final MemberRepository memberRepo;
    private final RegistrationRepository registrationRepo;
    private final InvoiceRepository invoiceRepo;
    private final UserRepository userRepo;
    private final PendingSelfCheckInStore hangDoiTuCheckIn;
    private final SystemSettingService settings;

    /**
     * Xử lý một lượt quét vào.
     *
     * <p>Luôn GHI LẠI kết quả dù cho vào hay từ chối. Phương thức này không ném
     * ngoại lệ khi từ chối — nó trả về bản ghi có {@code result} tương ứng, để
     * màn hình quầy hiển thị đúng thông báo và hệ thống vẫn thống kê được.
     */
    @Transactional
    public CheckInResponse quetVao(Long memberId, Long leTanUserId, boolean boQuaCanhBao) {

        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElse(null);

        if (m == null) {
            // Không có hội viên thì không ghi được dòng nào (khóa ngoại bắt buộc),
            // nên báo lỗi luôn — màn hình quầy hiện ô tìm theo số điện thoại.
            throw ApiException.notFound("Không tìm thấy hội viên");
        }

        // Xử lý xong qua đường nào (tìm tay hay hàng đợi tự check-in) cũng coi như
        // đã giải quyết yêu cầu — không để hội viên còn kẹt trong hàng đợi màn hình quầy.
        hangDoiTuCheckIn.xoaYeuCau(memberId);

        CheckIn c = new CheckIn();
        c.setMember(m);
        c.setMethod(CheckInMethod.QR_DYNAMIC);
        userRepo.findById(leTanUserId).ifPresent(c::setVerifiedBy);

        // ---- Bị cấm cửa thì chặn ngay, không xét tiếp ----
        if (m.getStatus() == MemberStatus.BLACKLISTED) {
            c.setResult(CheckInResult.DENIED_SUSPECT);
            c.setIncidentNote("Hội viên trong danh sách hạn chế");
            return ghiNhan(c);
        }

        // ---- Đang ở trong phòng tập (cùng ngày) mà lại quét vào lần nữa → CHẶN ----
        // Trước đây chỉ đánh dấu "bất thường" để thống kê rồi vẫn cho vào — dẫn tới
        // lễ tân bấm nhiều lần là ghi trùng nhiều lượt "đang trong phòng" cho cùng
        // một người. Lượt của NGÀY HÔM TRƯỚC thì bỏ qua, vì gần như chắc chắn là
        // quên quét lúc về chứ không phải đang thực sự ở trong phòng.
        var luotDangMoCungNgay = timLuotDangMoCungNgay(m.getId());
        if (luotDangMoCungNgay.isPresent()) {
            c.setResult(CheckInResult.DENIED_ALREADY_INSIDE);
            c.setIncidentType(IncidentType.SUSPECTED_SHARING);
            c.setIncidentNote("Đã quét vào lúc " + luotDangMoCungNgay.get().getCheckedInAt()
                    + ", chưa quét ra — nghi dùng chung tài khoản hoặc quét nhầm lần hai");
            return ghiNhan(c);
        }

        // ---- Phát hiện bất thường khác (quét lại quá nhanh sau khi đã quét ra) ----
        phatHienBatThuong(c, m);

        // ---- Tìm hợp đồng dùng để vào ----
        List<Registration> hopDongs =
                registrationRepo.findByMemberIdAndDeletedAtIsNullOrderByContractDateDesc(m.getId());
        TrangThaiHopDong tt = xacDinhTrangThaiHopDong(hopDongs, LocalDate.now());

        if (tt.dangChay() == null) {
            if (tt.dangBaoLuu()) {
                c.setResult(CheckInResult.DENIED_FROZEN);
            } else if (tt.choThanhToan() != null) {
                c.setResult(CheckInResult.DENIED_UNPAID);
                c.setRegistration(tt.choThanhToan());
                c.setIncidentNote("Hợp đồng chưa thanh toán: " + tt.choThanhToan().getRegistrationCode());
            } else {
                c.setResult(CheckInResult.DENIED_EXPIRED);
                if (c.getIncidentType() == null) {
                    c.setIncidentType(IncidentType.EXPIRED_ATTEMPT);
                }
            }
            return ghiNhan(c);
        }

        c.setRegistration(tt.dangChay());

        // ---- Còn nợ tiền: cảnh báo, nhưng lễ tân được phép cho vào ----
        if (tt.conNo() && !boQuaCanhBao) {
            c.setResult(CheckInResult.DENIED_UNPAID);
            return ghiNhan(c);
        }

        // Lễ tân chủ động bỏ qua cảnh báo — ghi lại rõ để truy trách nhiệm
        c.setResult(tt.conNo() ? CheckInResult.ALLOWED_OVERRIDE : CheckInResult.ALLOWED);
        return ghiNhan(c);
    }

    /** Kết quả tra cứu hợp đồng dùng chung giữa {@link #quetVao} và {@link #xemTruoc}. */
    private record TrangThaiHopDong(Registration dangChay, boolean dangBaoLuu,
                                     Registration choThanhToan, boolean conNo) {}

    /** Xác định hợp đồng đang chạy (và tình trạng nợ tiền) hoặc lý do không có hợp đồng nào dùng được. */
    private TrangThaiHopDong xacDinhTrangThaiHopDong(List<Registration> hopDongs, LocalDate homNay) {
        Registration dangChay = hopDongs.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.ACTIVE
                          && (r.getEndDate() == null || !r.getEndDate().isBefore(homNay)))
                .findFirst().orElse(null);

        if (dangChay == null) {
            boolean dangBaoLuu = hopDongs.stream()
                    .anyMatch(r -> r.getStatus() == RegistrationStatus.FROZEN);
            Registration choThanhToan = hopDongs.stream()
                    .filter(r -> r.getStatus() == RegistrationStatus.PENDING_PAYMENT)
                    .findFirst().orElse(null);
            return new TrangThaiHopDong(null, dangBaoLuu, choThanhToan, false);
        }

        boolean conNo = invoiceRepo.findByRegistrationIdAndDeletedAtIsNull(dangChay.getId())
                .map(inv -> inv.getStatus() == InvoiceStatus.UNPAID
                         || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID
                         || inv.getStatus() == InvoiceStatus.OVERDUE)
                .orElse(false);
        return new TrangThaiHopDong(dangChay, false, null, conNo);
    }

    /** Lượt vào chưa quét ra, TÍNH TỪ ĐÚNG NGÀY HÔM NAY — dùng chung giữa {@link #quetVao} và {@link #xemTruoc}. */
    private java.util.Optional<CheckIn> timLuotDangMoCungNgay(Long memberId) {
        return checkInRepo.findFirstByMemberIdAndCheckedOutAtIsNullOrderByCheckedInAtDesc(memberId)
                .filter(luotCu -> luotCu.getCheckedInAt().toLocalDate().equals(LocalDate.now()));
    }

    /**
     * Ghi nhận dấu hiệu bất thường CÒN LẠI sau khi đã loại trường hợp "đang ở
     * trong phòng" (trường hợp đó giờ bị chặn hẳn ở {@link #quetVao}, không tới
     * đây nữa). Không chặn lượt vào — chỉ đánh dấu để lễ tân chú ý và để hệ
     * thống thống kê. Quyết định cuối vẫn thuộc về người đứng quầy.
     */
    private void phatHienBatThuong(CheckIn c, Member m) {
        // Quét lại quá nhanh sau lượt trước
        int phutChongQuetLai = settings.getInt("checkin.duplicate-scan-window-minutes", 30);
        checkInRepo.findFirstByMemberIdOrderByCheckedInAtDesc(m.getId()).ifPresent(luotCu -> {
            long phut = Duration.between(luotCu.getCheckedInAt(), OffsetDateTime.now()).toMinutes();
            if (phut < phutChongQuetLai && luotCu.getCheckedOutAt() != null) {
                c.setIncidentType(IncidentType.ANTI_PASSBACK);
                c.setIncidentNote("Quét lại sau " + phut + " phút");
            }
        });
    }

    /**
     * Xem trước tình trạng hội viên — KHÔNG ghi lượt check-in nào.
     *
     * <p>Dùng chung {@link #xacDinhTrangThaiHopDong} với {@link #quetVao} để tra hợp đồng,
     * nhưng KHÔNG dùng chung phần quyết định kết quả cuối: {@link #quetVao} còn phải ghi
     * lại lượt bị từ chối (để thống kê) và xử lý cờ vượt cảnh báo, còn hàm này chỉ đọc và
     * trả lời "có vào được không" cho lễ tân xem trước khi bấm xác nhận.
     */
    @Transactional(readOnly = true)
    public CheckInPreviewResponse xemTruoc(Long memberId) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));

        if (m.getStatus() == MemberStatus.BLACKLISTED) {
            return CheckInPreviewResponse.of(m, CheckInResult.DENIED_SUSPECT, null);
        }

        if (timLuotDangMoCungNgay(m.getId()).isPresent()) {
            return CheckInPreviewResponse.of(m, CheckInResult.DENIED_ALREADY_INSIDE, null);
        }

        List<Registration> hopDongs =
                registrationRepo.findByMemberIdAndDeletedAtIsNullOrderByContractDateDesc(m.getId());
        TrangThaiHopDong tt = xacDinhTrangThaiHopDong(hopDongs, LocalDate.now());

        if (tt.dangChay() == null) {
            if (tt.dangBaoLuu()) {
                return CheckInPreviewResponse.of(m, CheckInResult.DENIED_FROZEN, null);
            } else if (tt.choThanhToan() != null) {
                return CheckInPreviewResponse.of(m, CheckInResult.DENIED_UNPAID, tt.choThanhToan());
            } else {
                return CheckInPreviewResponse.of(m, CheckInResult.DENIED_EXPIRED, null);
            }
        }

        return CheckInPreviewResponse.of(m,
                tt.conNo() ? CheckInResult.DENIED_UNPAID : CheckInResult.ALLOWED, tt.dangChay());
    }

    /**
     * Hội viên tự bấm "Tôi đã đến phòng tập" trên app — CHƯA ghi lượt check-in nào,
     * chỉ đưa vào hàng đợi để màn hình quầy hiện tên + ảnh cho lễ tân đối chiếu.
     */
    @Transactional(readOnly = true)
    public CheckInPreviewResponse guiYeuCauTuCheckIn(Long actorUserId) {
        Member m = memberCuaUser(actorUserId);
        hangDoiTuCheckIn.themYeuCau(m.getId());
        return xemTruoc(m.getId());
    }

    /** Lễ tân bỏ qua một yêu cầu tự check-in (hội viên bỏ đi, hoặc bấm nhầm) mà không cho vào. */
    public void boQuaYeuCauTuCheckIn(Long memberId) {
        hangDoiTuCheckIn.xoaYeuCau(memberId);
    }

    /** Hàng đợi cho màn hình quầy — tính lại tình trạng mới nhất cho từng người, không dùng dữ liệu cũ lúc gửi yêu cầu. */
    @Transactional(readOnly = true)
    public List<CheckInPreviewResponse> hangDoiChoXacNhan() {
        return hangDoiTuCheckIn.danhSachDangCho().stream()
                .map(this::xemTruoc)
                .toList();
    }

    private Member memberCuaUser(Long userId) {
        var u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .orElseThrow(() -> ApiException.badRequest("NOT_A_MEMBER", "Tài khoản chưa có hồ sơ hội viên"));
    }

    /** Hội viên quét ra khi về. */
    @Transactional
    public CheckInResponse quetRa(Long memberId) {
        CheckIn c = checkInRepo
                .findFirstByMemberIdAndCheckedOutAtIsNullOrderByCheckedInAtDesc(memberId)
                .orElseThrow(() -> ApiException.badRequest("NOT_CHECKED_IN",
                        "Không có lượt vào nào đang mở"));

        c.setCheckedOutAt(OffsetDateTime.now());
        c.setAutoClosed(false);
        return CheckInResponse.from(c);
    }

    /**
     * Job đêm: tự đóng các lượt hội viên quên quét lúc về.
     *
     * <p>Không có việc này thì hôm sau hội viên quét vào, hệ thống thấy "đang ở
     * trong phòng mà lại vào" và báo nghi gian lận sai — cảnh báo kêu liên tục
     * sẽ mất hết giá trị.
     */
    @Transactional
    public int tuDongDongLuotQuenQuetRa() {
        var dauNgayHomNay = OffsetDateTime.now().with(LocalTime.MIN);
        List<CheckIn> quenQuet = checkInRepo.timLuotQuenCheckOut(dauNgayHomNay);

        LocalTime gioDongCua = settings.getLocalTime("gym.closing-time", LocalTime.of(22, 30));
        for (CheckIn c : quenQuet) {
            // Lấy giờ đóng cửa của ĐÚNG NGÀY hội viên vào, không phải hôm nay
            c.setCheckedOutAt(c.getCheckedInAt().with(gioDongCua));
            c.setAutoClosed(true);
        }

        if (!quenQuet.isEmpty()) {
            log.info("Tự đóng {} lượt check-in quên quét ra", quenQuet.size());
        }
        return quenQuet.size();
    }

    // ---------------------------------------------------------------- truy vấn

    @Transactional(readOnly = true)
    public List<CheckInResponse> lichSuCuaHoiVien(Long memberId) {
        return checkInRepo.findByMemberIdOrderByCheckedInAtDesc(memberId)
                .stream().map(CheckInResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> dangTrongPhongTap() {
        return checkInRepo.dangTrongPhongTap()
                .stream().map(CheckInResponse::from).toList();
    }

    /** Thống kê hiệu quả chống thất thoát trong N ngày gần nhất. */
    @Transactional(readOnly = true)
    public List<Object[]> thongKe(int soNgay) {
        return checkInRepo.thongKeTheoKetQua(OffsetDateTime.now().minusDays(soNgay));
    }

    private CheckInResponse ghiNhan(CheckIn c) {
        CheckIn daLuu = checkInRepo.save(c);
        if (c.getResult() != CheckInResult.ALLOWED) {
            log.info("Check-in #{}: {} — hội viên {}{}",
                    daLuu.getId(), c.getResult(), c.getMember().getMemberCode(),
                    c.getIncidentType() == null ? "" : " (" + c.getIncidentType() + ")");
        }
        return CheckInResponse.from(daLuu);
    }
}
