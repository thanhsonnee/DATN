package com.gym.checkin.service;

import com.gym.billing.domain.InvoiceStatus;
import com.gym.billing.repository.InvoiceRepository;
import com.gym.checkin.api.dto.CheckInPreviewResponse;
import com.gym.checkin.api.dto.CheckInResponse;
import com.gym.checkin.api.dto.CheckInSelfStatusResponse;
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

        // Khóa dòng hội viên ngay từ đầu: hai lượt quét gần như đồng thời cho cùng
        // một người (2 đầu đọc thẻ, hoặc bấm đúp) đều có thể đọc "chưa có lượt mở"
        // ở dưới trước khi lượt kia kịp ghi, nếu không khóa cả hai sẽ cùng ALLOWED.
        Member m = memberRepo.khoaHoiVien(memberId)
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

        // Tự dọn lượt quên quét ra của NGÀY TRƯỚC trước khi xét gì khác — nếu không, một
        // lượt tồn đọng do job đêm lỡ chạy (server tắt đúng lúc 23h) sẽ nằm mở song song
        // với lượt mới hôm nay, gây chồng lấn "đang trong phòng" và sai lệch số liệu.
        dongLuotMoTuNgayTruoc(memberId);

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
                .filter(r -> r.allowsCheckIn()
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
        return checkInRepo.timCacLuotDangMoThucSu(memberId).stream()
                .filter(luotCu -> luotCu.getCheckedInAt().toLocalDate().equals(LocalDate.now()))
                .findFirst();
    }

    /**
     * Ghi nhận dấu hiệu bất thường CÒN LẠI sau khi đã loại trường hợp "đang ở
     * trong phòng" (trường hợp đó giờ bị chặn hẳn ở {@link #quetVao}, không tới
     * đây nữa). Không chặn lượt vào — chỉ đánh dấu để lễ tân chú ý và để hệ
     * thống thống kê. Quyết định cuối vẫn thuộc về người đứng quầy.
     */
    private void phatHienBatThuong(CheckIn c, Member m) {
        // Quét lại quá nhanh SAU KHI ĐÃ QUÉT RA lượt trước — phải tính từ lúc RA
        // (checkedOutAt), không phải lúc VÀO (checkedInAt), nếu không một người tập lâu
        // (vài tiếng) rồi chuyền thẻ ra ngay sau khi ra sẽ không bị phát hiện, vì khoảng
        // cách tính từ lúc vào luôn lớn hơn ngưỡng dù họ vừa ra cửa chưa đầy 1 phút.
        int phutChongQuetLai = settings.getInt("checkin.duplicate-scan-window-minutes", 30);
        checkInRepo.findFirstByMemberIdOrderByCheckedInAtDesc(m.getId()).ifPresent(luotCu -> {
            if (luotCu.getCheckedOutAt() == null) {
                return; // lượt đang mở đã bị chặn hẳn ở bước "đang trong phòng", không tính ở đây
            }
            long phut = Duration.between(luotCu.getCheckedOutAt(), OffsetDateTime.now()).toMinutes();
            if (phut < phutChongQuetLai) {
                c.setIncidentType(IncidentType.ANTI_PASSBACK);
                c.setIncidentNote("Quét lại sau " + phut + " phút kể từ lúc quét ra lượt trước");
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
     *
     * <p>Nếu hội viên ĐANG THỰC SỰ Ở TRONG PHÒNG (chưa quét ra) thì KHÔNG đưa vào hàng
     * đợi — báo thẳng cho hội viên biết luôn, không làm phiền lễ tân bằng một yêu cầu
     * chắc chắn sẽ bị từ chối. Lý do sâu hơn: nếu vẫn đưa vào hàng đợi, lễ tân bấm "bỏ
     * qua" (hoặc hàng đợi tự hết hạn) sẽ xóa yêu cầu này khỏi hàng đợi mà KHÔNG ghi lượt
     * check-in nào mới — lúc đó {@link #trangThaiTuCheckIn} không còn gì để phân biệt với
     * lượt vào THẬT trước đó của chính hội viên này, nên lỡ vẫn còn trong cửa sổ 5 phút thì
     * lại hiện nhầm "Đã xác nhận — mời vào tập" cho một yêu cầu thực ra chưa từng được xử lý.
     */
    @Transactional(readOnly = true)
    public CheckInPreviewResponse guiYeuCauTuCheckIn(Long actorUserId) {
        Member m = memberCuaUser(actorUserId);
        CheckInPreviewResponse preview = xemTruoc(m.getId());
        if (!CheckInResult.DENIED_ALREADY_INSIDE.name().equals(preview.result())) {
            hangDoiTuCheckIn.themYeuCau(m.getId());
        }
        return preview;
    }

    /**
     * Lễ tân TỪ CHỐI một yêu cầu tự check-in trong hàng đợi (không cho vào), luôn kèm lý do.
     *
     * <p>Cố ý GHI LẠI một lượt {@code DENIED_MANUAL} thật thay vì chỉ xóa khỏi hàng đợi trong
     * bộ nhớ như trước — nếu không ghi gì cả, {@link #trangThaiTuCheckIn} không còn cách nào
     * phân biệt "bị từ chối" với "chưa từng gửi yêu cầu" hay với một lượt check-in KHÁC, không
     * liên quan, xảy ra tình cờ trong cùng khung 5 phút — dẫn tới hiện nhầm "Đã xác nhận — mời
     * vào tập" cho một yêu cầu thực ra vừa bị từ chối.
     */
    @Transactional
    public CheckInResponse tuChoiYeuCauTuCheckIn(Long memberId, Long leTanUserId, String lyDo) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));

        hangDoiTuCheckIn.xoaYeuCau(memberId);

        CheckIn c = new CheckIn();
        c.setMember(m);
        c.setMethod(CheckInMethod.QR_DYNAMIC);
        userRepo.findById(leTanUserId).ifPresent(c::setVerifiedBy);
        c.setResult(CheckInResult.DENIED_MANUAL);
        c.setIncidentNote(lyDo);
        return ghiNhan(c);
    }

    /**
     * Hội viên tự hỏi lại trạng thái yêu cầu tự check-in — app gọi lặp lại (poll) sau khi
     * gửi yêu cầu, để tự hiện thông báo ngay khi lễ tân xác nhận, không cần bấm làm mới tay.
     *
     * <p>Còn trong hàng đợi → PENDING. Ra khỏi hàng đợi mà có lượt check-in vừa ghi trong ít
     * phút gần đây → lễ tân vừa xử lý xong, trả CONFIRMED kèm đúng kết quả thật (có thể là
     * từ chối, nếu lúc lễ tân xử lý phát hiện hợp đồng có vấn đề). Không thấy gì cả → NONE.
     */
    @Transactional(readOnly = true)
    public CheckInSelfStatusResponse trangThaiTuCheckIn(Long actorUserId) {
        Member m = memberCuaUser(actorUserId);

        if (hangDoiTuCheckIn.dangCho(m.getId())) {
            var preview = xemTruoc(m.getId());
            return CheckInSelfStatusResponse.pending(preview.thongBao(), preview.choPhepVao());
        }

        return checkInRepo.findFirstByMemberIdOrderByCheckedInAtDesc(m.getId())
                .filter(c -> Duration.between(c.getCheckedInAt(), OffsetDateTime.now()).toMinutes() <= 5)
                .map(c -> CheckInSelfStatusResponse.confirmed(CheckInResponse.from(c)))
                .orElseGet(CheckInSelfStatusResponse::none);
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
        // Dọn trước các lượt quên quét ra của NGÀY TRƯỚC (nếu có) — để lượt thật sự cần
        // đóng bằng thao tác này luôn là lượt CỦA HÔM NAY, không lẫn với lượt tồn đọng cũ.
        dongLuotMoTuNgayTruoc(memberId);

        CheckIn c = checkInRepo.timCacLuotDangMoThucSu(memberId).stream().findFirst()
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
        quenQuet.forEach(c -> dongLuotQuen(c, gioDongCua));

        if (!quenQuet.isEmpty()) {
            log.info("Tự đóng {} lượt check-in quên quét ra", quenQuet.size());
        }
        return quenQuet.size();
    }

    /**
     * Tự đóng ngay các lượt CỦA NGÀY TRƯỚC còn mở của một hội viên, ngay khi hội viên đó
     * có tương tác mới (quét vào/ra) — không đợi job đêm.
     *
     * <p>Job {@link #tuDongDongLuotQuenQuetRa} chỉ chạy nếu server đang sống đúng lúc cron
     * kích hoạt; nếu server khởi động lại quanh mốc đó (rất thường gặp khi đang phát
     * triển, và có thể xảy ra cả khi deploy production), lượt quên quét ra có thể tồn
     * đọng nhiều ngày. Trong lúc đó, hội viên vẫn quét vào bình thường được (vì lượt tồn
     * đọng không cùng ngày nên không bị chặn "đang trong phòng"), tạo ra HAI lượt mở song
     * song cho cùng một người — sai cả "ai đang trong phòng" lẫn số liệu thời lượng tập.
     * Chốt lại ở đây mỗi lần có tương tác để không phải phụ thuộc hoàn toàn vào cron.
     */
    private void dongLuotMoTuNgayTruoc(Long memberId) {
        LocalDate homNay = LocalDate.now();
        LocalTime gioDongCua = settings.getLocalTime("gym.closing-time", LocalTime.of(22, 30));
        checkInRepo.timCacLuotDangMoThucSu(memberId).stream()
                .filter(c -> c.getCheckedInAt().toLocalDate().isBefore(homNay))
                .forEach(c -> dongLuotQuen(c, gioDongCua));
    }

    /** Đóng một lượt quên quét ra bằng giờ đóng cửa của ĐÚNG NGÀY hội viên vào, không phải hôm nay. */
    private void dongLuotQuen(CheckIn c, LocalTime gioDongCua) {
        c.setCheckedOutAt(c.getCheckedInAt().with(gioDongCua));
        c.setAutoClosed(true);
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
