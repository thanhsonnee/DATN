package com.gym.training.service;

import com.gym.common.exception.ApiException;
import com.gym.identity.domain.*;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.domain.Registration;
import com.gym.membership.domain.RegistrationStatus;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.training.domain.*;
import com.gym.training.api.dto.PtSessionResponse;
import com.gym.training.repository.PtSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Vòng đời buổi tập với huấn luyện viên.
 *
 * <h3>Xác nhận hai chiều — cơ chế chống khai khống</h3>
 * Buổi tập chỉ được tính công và trừ buổi khi <b>cả hai bên cùng xác nhận</b>:
 * <ol>
 *   <li>Huấn luyện viên bấm "Kết thúc" — đây là <b>điều kiện khởi động</b>.
 *       Không bấm thì không có gì xảy ra, nên buổi không dạy sẽ không bao giờ
 *       tự động được trả công.</li>
 *   <li>Hội viên bấm "Xác nhận đã tập".</li>
 * </ol>
 * Hội viên không phản hồi trong 24 giờ thì hệ thống tự duyệt, nhưng đánh dấu
 * {@code autoConfirmed} để kiểm toán — tỷ lệ tự duyệt cao là dấu hiệu đáng ngờ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PtSessionService {

    // Tầng dịch vụ trả về DTO chứ không trả entity: dữ liệu liên kết chỉ tải được
    // khi còn trong giao dịch. Dựng DTO ở controller sẽ gặp lỗi vì lúc đó giao dịch
    // đã đóng — và controller cũng không nên biết tới entity.

    /** Hủy sát giờ hơn ngần này thì mất buổi. Sẽ chuyển sang system_settings. */
    private static final int GIO_HUY_MUON = 4;

    /** Hội viên không xác nhận quá ngần này giờ thì hệ thống tự duyệt. */
    private static final int GIO_TU_DUYET = 24;

    private final PtSessionRepository sessionRepo;
    private final RegistrationRepository registrationRepo;
    private final MemberRepository memberRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final SessionCreditLedgerService soCai;

    // ------------------------------------------------------------- đặt lịch

    /**
     * Hội viên đặt lịch tập. Buổi ở trạng thái chờ huấn luyện viên duyệt.
     *
     * <p>Kiểm tra số buổi còn lại NGAY LÚC ĐẶT để báo sớm cho hội viên, nhưng
     * <b>chưa trừ buổi</b> — chỉ trừ khi buổi thực sự hoàn thành. Đặt lịch rồi hủy
     * không được làm mất buổi của hội viên.
     */
    @Transactional
    public PtSessionResponse datLich(Long actorUserId, Long registrationId, Long trainerId,
                             OffsetDateTime batDau, OffsetDateTime ketThuc,
                             SessionType loaiBuoi, String phongTap) {

        Registration hopDong = registrationRepo.findById(registrationId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng"));

        if (hopDong.getStatus() != RegistrationStatus.ACTIVE) {
            throw ApiException.badRequest("CONTRACT_NOT_ACTIVE",
                    "Hợp đồng đang ở trạng thái " + hopDong.getStatus()
                            + ", không đặt lịch tập được");
        }
        if (!ketThuc.isAfter(batDau)) {
            throw ApiException.badRequest("INVALID_TIME", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (batDau.isBefore(OffsetDateTime.now())) {
            throw ApiException.badRequest("PAST_TIME", "Không đặt lịch cho thời điểm đã qua");
        }

        Employee trainer = employeeRepo.findById(trainerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy huấn luyện viên"));
        if (!trainer.isTrainer()) {
            throw ApiException.badRequest("NOT_A_TRAINER",
                    "Nhân viên được chọn không phải huấn luyện viên");
        }
        if (trainer.getStatus() != EmployeeStatus.ACTIVE) {
            throw ApiException.badRequest("TRAINER_UNAVAILABLE",
                    "Huấn luyện viên hiện không nhận lịch");
        }

        // Báo sớm nếu hết buổi, nhưng CHƯA trừ — chỉ trừ khi buổi hoàn thành
        SessionType loai = loaiBuoi == null ? SessionType.PAID_PT : loaiBuoi;
        if (loai == SessionType.PAID_PT && soCai.soDuHienTai(registrationId) <= 0) {
            throw ApiException.conflict("NO_CREDIT_LEFT",
                    "Bạn đã dùng hết số buổi trong gói này");
        }

        PtSession s = new PtSession();
        s.setMember(hopDong.getMember());
        s.setTrainer(trainer);
        s.setRegistration(hopDong);
        s.setSessionType(loai);
        s.setScheduledStart(batDau);
        s.setScheduledEnd(ketThuc);
        s.setRoomName(phongTap);
        s.setStatus(SessionStatus.PENDING_TRAINER);
        userRepo.findById(actorUserId).ifPresent(s::setRequestedBy);

        s = sessionRepo.save(s);
        log.info("Đặt lịch buổi tập #{}: hội viên {} với PT {} lúc {}",
                s.getId(), hopDong.getMember().getMemberCode(), trainer.getEmployeeCode(), batDau);
        return PtSessionResponse.from(s);
    }

    /** Huấn luyện viên duyệt yêu cầu đặt lịch. */
    @Transactional
    public PtSessionResponse duyetLich(Long sessionId) {
        PtSession s = require(sessionId);
        phaiOTrangThai(s, SessionStatus.PENDING_TRAINER);

        s.setStatus(SessionStatus.SCHEDULED);
        s.setRespondedAt(OffsetDateTime.now());
        return PtSessionResponse.from(s);
    }

    /** Huấn luyện viên từ chối, bắt buộc nêu lý do để hội viên biết đường xếp lại. */
    @Transactional
    public PtSessionResponse tuChoiLich(Long sessionId, String lyDo) {
        PtSession s = require(sessionId);
        phaiOTrangThai(s, SessionStatus.PENDING_TRAINER);

        if (lyDo == null || lyDo.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Từ chối lịch phải nêu lý do");
        }
        s.setStatus(SessionStatus.REJECTED);
        s.setRejectReason(lyDo);
        s.setRespondedAt(OffsetDateTime.now());
        return PtSessionResponse.from(s);
    }

    // ---------------------------------------------------- xác nhận hai chiều

    /**
     * Huấn luyện viên bấm "Kết thúc buổi tập" — bước một của xác nhận hai chiều.
     *
     * <p>Đây là điều kiện khởi động của cả quy trình tính công. Mặc định của hệ
     * thống là KHÔNG trả tiền, KHÔNG trừ buổi, trừ khi huấn luyện viên chủ động
     * bấm trước. Gánh nặng chứng minh "có dạy" thuộc về người muốn được trả công.
     */
    @Transactional
    public PtSessionResponse huanLuyenVienXacNhan(Long sessionId, Long actorUserId) {
        PtSession s = require(sessionId);
        phaiOTrangThai(s, SessionStatus.SCHEDULED);
        phaiLaHuanLuyenVienCuaBuoi(s, actorUserId);

        if (s.getTrainerConfirmedAt() != null) {
            throw ApiException.conflict("ALREADY_CONFIRMED", "Bạn đã xác nhận buổi tập này rồi");
        }

        OffsetDateTime bayGio = OffsetDateTime.now();
        s.setTrainerConfirmedAt(bayGio);

        if (s.getActualEnd() == null) s.setActualEnd(bayGio);
        if (s.getActualStart() == null) {
            // Lấy giờ hẹn làm giờ bắt đầu thực tế, NHƯNG không được muộn hơn giờ
            // kết thúc. Nếu buổi được xác nhận trước cả giờ hẹn (đổi lịch sớm,
            // hoặc dữ liệu thử) thì lấy chính thời điểm xác nhận.
            OffsetDateTime batDauThucTe = s.getScheduledStart().isAfter(s.getActualEnd())
                    ? s.getActualEnd()
                    : s.getScheduledStart();
            s.setActualStart(batDauThucTe);
        }

        log.info("Huấn luyện viên xác nhận buổi #{}, chờ hội viên phản hồi", sessionId);
        return PtSessionResponse.from(hoanTatNeuDuHaiBen(s, false));
    }

    /** Hội viên bấm "Xác nhận đã tập" — bước hai của xác nhận hai chiều. */
    @Transactional
    public PtSessionResponse hoiVienXacNhan(Long sessionId, Long actorUserId) {
        PtSession s = require(sessionId);
        phaiOTrangThai(s, SessionStatus.SCHEDULED);
        phaiLaHoiVienCuaBuoi(s, actorUserId);

        if (s.getTrainerConfirmedAt() == null) {
            throw ApiException.badRequest("TRAINER_NOT_CONFIRMED",
                    "Huấn luyện viên chưa xác nhận kết thúc buổi tập");
        }
        if (s.getMemberConfirmedAt() != null) {
            throw ApiException.conflict("ALREADY_CONFIRMED", "Bạn đã xác nhận buổi tập này rồi");
        }

        s.setMemberConfirmedAt(OffsetDateTime.now());
        return PtSessionResponse.from(hoanTatNeuDuHaiBen(s, false));
    }

    /**
     * Chuyển buổi sang hoàn thành khi đã đủ hai xác nhận, và trừ buổi trong sổ cái.
     *
     * <p>Việc trừ buổi nằm CÙNG GIAO DỊCH với việc đổi trạng thái: hoặc cả hai
     * cùng thành công, hoặc cả hai cùng quay lui. Không thể có chuyện buổi được
     * đánh dấu hoàn thành mà số dư không đổi, hay ngược lại.
     */
    private PtSession hoanTatNeuDuHaiBen(PtSession s, boolean tuDuyet) {
        if (!s.daXacNhanDuHaiBen()) return s;

        s.setStatus(SessionStatus.COMPLETED);
        s.setAutoConfirmed(tuDuyet);

        // Chỉ buổi có trả phí mới trừ buổi. Buổi hỗ trợ miễn phí trừ 0 buổi của
        // hội viên nhưng huấn luyện viên VẪN được tính công ở bảng lương.
        if (s.truBuoiCuaHoiVien()) {
            soCai.truBuoi(s.getRegistration(), s.getId());
        }

        log.info("Buổi #{} hoàn thành ({}), loại {}", s.getId(),
                tuDuyet ? "hệ thống tự duyệt" : "đủ hai xác nhận", s.getSessionType());
        return s;
    }

    /**
     * Job đêm: tự duyệt buổi mà hội viên không phản hồi quá 24 giờ.
     *
     * <p>Cờ {@code autoConfirmed} được bật để kiểm toán — nếu một huấn luyện viên
     * có tỷ lệ tự duyệt cao bất thường, đó là dấu hiệu cần xem lại.
     */
    @Transactional
    public int tuDuyetBuoiQuaHan() {
        var hetHan = OffsetDateTime.now().minusHours(GIO_TU_DUYET);
        List<PtSession> danhSach = sessionRepo.timBuoiChoHoiVienXacNhan(hetHan);

        for (PtSession s : danhSach) {
            s.setMemberConfirmedAt(OffsetDateTime.now());
            hoanTatNeuDuHaiBen(s, true);
        }
        if (!danhSach.isEmpty()) {
            log.info("Tự duyệt {} buổi tập quá hạn chờ hội viên xác nhận", danhSach.size());
        }
        return danhSach.size();
    }

    // ------------------------------------------------------------ hủy, vắng

    /**
     * Hủy buổi tập.
     *
     * <p>Hủy trước giờ hẹn đủ xa thì buổi được hoàn lại vào sổ cái. Hủy sát giờ
     * thì mất buổi — vì huấn luyện viên đã giữ chỗ và không kịp xếp lịch khác.
     */
    @Transactional
    public PtSessionResponse huyBuoi(Long sessionId, ActorSide benHuy, String lyDo) {
        PtSession s = require(sessionId);

        if (s.getStatus() != SessionStatus.PENDING_TRAINER
                && s.getStatus() != SessionStatus.SCHEDULED) {
            throw ApiException.badRequest("INVALID_STATE",
                    "Buổi tập đang ở trạng thái " + s.getStatus() + ", không hủy được");
        }

        long gioConLai = Duration.between(OffsetDateTime.now(), s.getScheduledStart()).toHours();
        boolean huyMuon = gioConLai < GIO_HUY_MUON;

        s.setStatus(SessionStatus.CANCELLED);
        s.setCancelledBy(benHuy);
        s.setCancelledAt(OffsetDateTime.now());
        s.setCancelReason(lyDo);
        // Huấn luyện viên hủy thì không bao giờ tính là hủy muộn — lỗi không
        // thuộc về hội viên nên không được lấy buổi của họ
        s.setIsLateCancel(huyMuon && benHuy == ActorSide.MEMBER);

        log.info("Hủy buổi #{} bởi {}, còn {} giờ tới giờ hẹn, hủy muộn={}",
                sessionId, benHuy, gioConLai, s.getIsLateCancel());
        return PtSessionResponse.from(s);
    }

    /**
     * Đánh dấu vắng mặt.
     *
     * <p>Hội viên vắng thì mất buổi. Huấn luyện viên vắng thì hội viên không mất
     * gì — buổi vẫn còn nguyên trong sổ cái để đặt lại.
     */
    @Transactional
    public PtSessionResponse danhDauVangMat(Long sessionId, ActorSide benVang, String ghiChu) {
        PtSession s = require(sessionId);
        phaiOTrangThai(s, SessionStatus.SCHEDULED);

        if (benVang != ActorSide.MEMBER && benVang != ActorSide.TRAINER) {
            throw ApiException.badRequest("INVALID_ACTOR",
                    "Chỉ ghi nhận vắng mặt của hội viên hoặc huấn luyện viên");
        }

        s.setStatus(SessionStatus.NO_SHOW);
        s.setNoShowBy(benVang);
        s.setNote(ghiChu);

        // Hội viên vắng: buổi coi như đã tiêu. Huấn luyện viên vắng: giữ nguyên buổi.
        if (benVang == ActorSide.MEMBER && s.truBuoiCuaHoiVien()) {
            soCai.truBuoi(s.getRegistration(), s.getId());
        }

        log.info("Buổi #{} vắng mặt bởi {}", sessionId, benVang);
        return PtSessionResponse.from(s);
    }

    // ---------------------------------------------------------------- truy vấn

    @Transactional(readOnly = true)
    public List<PtSessionResponse> buoiTapCuaHoiVien(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(m -> sessionRepo.findByMemberIdAndDeletedAtIsNullOrderByScheduledStartDesc(m.getId())
                        .stream().map(PtSessionResponse::from).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<PtSessionResponse> lichDayCuaHuanLuyenVien(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(e -> sessionRepo.findByTrainerIdAndDeletedAtIsNullOrderByScheduledStartDesc(e.getId())
                        .stream().map(PtSessionResponse::from).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public PtSessionResponse chiTiet(Long sessionId) {
        return PtSessionResponse.from(require(sessionId));
    }

    // ---------------------------------------------------------------- riêng tư

    private PtSession require(Long id) {
        return sessionRepo.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy buổi tập"));
    }

    private void phaiOTrangThai(PtSession s, SessionStatus mongDoi) {
        if (s.getStatus() != mongDoi) {
            throw ApiException.badRequest("INVALID_STATE",
                    "Buổi tập đang ở trạng thái " + s.getStatus()
                            + ", thao tác này chỉ áp dụng cho trạng thái " + mongDoi);
        }
    }

    /** Không cho huấn luyện viên xác nhận hộ buổi của người khác. */
    private void phaiLaHuanLuyenVienCuaBuoi(PtSession s, Long actorUserId) {
        User u = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        if (u.getPrimaryRole() == UserRole.ADMIN) return;

        boolean dungNguoi = employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(e -> e.getId().equals(s.getTrainer().getId()))
                .orElse(false);

        if (!dungNguoi) {
            throw ApiException.forbidden("NOT_YOUR_SESSION",
                    "Bạn không phải huấn luyện viên của buổi tập này");
        }
    }

    /** Không cho hội viên xác nhận hộ buổi của người khác. */
    private void phaiLaHoiVienCuaBuoi(PtSession s, Long actorUserId) {
        User u = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        if (u.getPrimaryRole() == UserRole.ADMIN) return;

        boolean dungNguoi = memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(m -> m.getId().equals(s.getMember().getId()))
                .orElse(false);

        if (!dungNguoi) {
            throw ApiException.forbidden("NOT_YOUR_SESSION",
                    "Đây không phải buổi tập của bạn");
        }
    }
}
