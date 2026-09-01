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

    /** Quét lại trong khoảng này bị coi là bất thường. Sẽ chuyển sang system_settings. */
    private static final int PHUT_CHONG_QUET_LAI = 30;

    /** Giờ đóng cửa, dùng cho job tự đóng lượt quên quét. */
    private static final LocalTime GIO_DONG_CUA = LocalTime.of(22, 30);

    private final CheckInRepository checkInRepo;
    private final MemberRepository memberRepo;
    private final RegistrationRepository registrationRepo;
    private final InvoiceRepository invoiceRepo;
    private final UserRepository userRepo;

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

        // ---- Phát hiện bất thường ----
        phatHienBatThuong(c, m);

        // ---- Tìm hợp đồng dùng để vào ----
        List<Registration> hopDongs =
                registrationRepo.findByMemberIdAndDeletedAtIsNullOrderByContractDateDesc(m.getId());

        LocalDate homNay = LocalDate.now();
        Registration dangChay = hopDongs.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.ACTIVE
                          && (r.getEndDate() == null || !r.getEndDate().isBefore(homNay)))
                .findFirst().orElse(null);

        if (dangChay == null) {
            boolean dangBaoLuu = hopDongs.stream()
                    .anyMatch(r -> r.getStatus() == RegistrationStatus.FROZEN);

            c.setResult(dangBaoLuu ? CheckInResult.DENIED_FROZEN : CheckInResult.DENIED_EXPIRED);
            if (c.getIncidentType() == null && !dangBaoLuu) {
                c.setIncidentType(IncidentType.EXPIRED_ATTEMPT);
            }
            return ghiNhan(c);
        }

        c.setRegistration(dangChay);

        // ---- Còn nợ tiền: cảnh báo, nhưng lễ tân được phép cho vào ----
        boolean conNo = invoiceRepo.findByRegistrationIdAndDeletedAtIsNull(dangChay.getId())
                .map(inv -> inv.getStatus() == InvoiceStatus.UNPAID
                         || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID
                         || inv.getStatus() == InvoiceStatus.OVERDUE)
                .orElse(false);

        if (conNo && !boQuaCanhBao) {
            c.setResult(CheckInResult.DENIED_UNPAID);
            return ghiNhan(c);
        }

        // Lễ tân chủ động bỏ qua cảnh báo — ghi lại rõ để truy trách nhiệm
        c.setResult(conNo ? CheckInResult.ALLOWED_OVERRIDE : CheckInResult.ALLOWED);
        return ghiNhan(c);
    }

    /**
     * Ghi nhận các dấu hiệu bất thường.
     *
     * <p>Không chặn lượt vào — chỉ đánh dấu để lễ tân chú ý và để hệ thống thống
     * kê. Quyết định cuối vẫn thuộc về người đứng quầy.
     */
    private void phatHienBatThuong(CheckIn c, Member m) {
        // Đang ở TRONG phòng tập mà lại có lượt vào mới → nghi dùng chung tài khoản
        var dangTrongPhong = checkInRepo
                .findFirstByMemberIdAndCheckedOutAtIsNullOrderByCheckedInAtDesc(m.getId());

        if (dangTrongPhong.isPresent()) {
            var luotCu = dangTrongPhong.get();
            // Chỉ đáng nghi nếu là TRONG CÙNG NGÀY. Lượt của hôm trước gần như
            // chắc chắn là do quên quét lúc về, không phải gian lận.
            boolean cungNgay = luotCu.getCheckedInAt().toLocalDate().equals(LocalDate.now());
            if (cungNgay) {
                c.setIncidentType(IncidentType.SUSPECTED_SHARING);
                c.setIncidentNote("Lượt vào lúc " + luotCu.getCheckedInAt()
                        + " chưa quét ra, nghi dùng chung tài khoản");
                return;
            }
        }

        // Quét lại quá nhanh sau lượt trước
        checkInRepo.findFirstByMemberIdOrderByCheckedInAtDesc(m.getId()).ifPresent(luotCu -> {
            long phut = Duration.between(luotCu.getCheckedInAt(), OffsetDateTime.now()).toMinutes();
            if (phut < PHUT_CHONG_QUET_LAI && luotCu.getCheckedOutAt() != null) {
                c.setIncidentType(IncidentType.ANTI_PASSBACK);
                c.setIncidentNote("Quét lại sau " + phut + " phút");
            }
        });
    }

    /**
     * Xem trước tình trạng hội viên — KHÔNG ghi lượt check-in nào.
     *
     * <p>Cố tình KHÔNG dùng chung code với {@link #quetVao}: hàm đó phải ghi lại
     * cả lượt bị từ chối (để thống kê) và xử lý cờ vượt cảnh báo, còn hàm này chỉ
     * đọc và trả lời "có vào được không" cho lễ tân xem trước khi bấm xác nhận.
     * Tách riêng để không phải sửa logic đã có 19 test bao phủ trong quetVao.
     */
    @Transactional(readOnly = true)
    public CheckInPreviewResponse xemTruoc(Long memberId) {
        Member m = memberRepo.findById(memberId)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));

        if (m.getStatus() == MemberStatus.BLACKLISTED) {
            return CheckInPreviewResponse.of(m, CheckInResult.DENIED_SUSPECT, null);
        }

        List<Registration> hopDongs =
                registrationRepo.findByMemberIdAndDeletedAtIsNullOrderByContractDateDesc(m.getId());
        LocalDate homNay = LocalDate.now();
        Registration dangChay = hopDongs.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.ACTIVE
                          && (r.getEndDate() == null || !r.getEndDate().isBefore(homNay)))
                .findFirst().orElse(null);

        if (dangChay == null) {
            boolean dangBaoLuu = hopDongs.stream()
                    .anyMatch(r -> r.getStatus() == RegistrationStatus.FROZEN);
            return CheckInPreviewResponse.of(m,
                    dangBaoLuu ? CheckInResult.DENIED_FROZEN : CheckInResult.DENIED_EXPIRED, null);
        }

        boolean conNo = invoiceRepo.findByRegistrationIdAndDeletedAtIsNull(dangChay.getId())
                .map(inv -> inv.getStatus() == InvoiceStatus.UNPAID
                         || inv.getStatus() == InvoiceStatus.PARTIALLY_PAID
                         || inv.getStatus() == InvoiceStatus.OVERDUE)
                .orElse(false);

        return CheckInPreviewResponse.of(m,
                conNo ? CheckInResult.DENIED_UNPAID : CheckInResult.ALLOWED, dangChay);
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

        for (CheckIn c : quenQuet) {
            // Lấy giờ đóng cửa của ĐÚNG NGÀY hội viên vào, không phải hôm nay
            c.setCheckedOutAt(c.getCheckedInAt().with(GIO_DONG_CUA));
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
