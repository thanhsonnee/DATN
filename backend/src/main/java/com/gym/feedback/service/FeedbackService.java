package com.gym.feedback.service;

import com.gym.billing.api.dto.CreateExpenseRequest;
import com.gym.billing.domain.ExpenseCategory;
import com.gym.billing.domain.PaymentMethod;
import com.gym.billing.service.ExpenseService;
import com.gym.common.exception.ApiException;
import com.gym.feedback.api.dto.CreateFeedbackRequest;
import com.gym.feedback.api.dto.FeedbackResponse;
import com.gym.feedback.api.dto.UpdateFeedbackRequest;
import com.gym.feedback.domain.Equipment;
import com.gym.feedback.domain.EquipmentStatus;
import com.gym.feedback.domain.Feedback;
import com.gym.feedback.domain.FeedbackStatus;
import com.gym.feedback.domain.FeedbackType;
import com.gym.feedback.repository.FeedbackRepository;
import com.gym.identity.domain.Employee;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phân đoạn H: Phản hồi hội viên & sự cố thiết bị.
 *
 * <p>Định tuyến xảy ra ngay lúc tạo, theo {@link FeedbackType}:
 * <ul>
 *   <li>{@code TRAINER} — cộng dồn vào {@code employees.rating_avg}; rating ≤ 2 thì
 *       bật cờ {@code urgent} để hàng đợi Admin lọc ưu tiên (thay cho hệ thống task
 *       tổng quát chưa xây).</li>
 *   <li>{@code FACILITY} — chuyển thiết bị sang {@code NEEDS_REPAIR} ngay.</li>
 *   <li>{@code HYGIENE}/{@code SERVICE}/{@code GENERAL} — chỉ vào hàng đợi xử lý,
 *       không có hiệu ứng phụ.</li>
 * </ul>
 * Đóng xong một phản hồi FACILITY có chi phí sửa thì tự sinh 1 dòng
 * {@code expenses(EQUIPMENT_MAINTENANCE)} — nối thẳng sang module Chi phí (E5) thay vì
 * bắt kế toán nhập tay lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** OPEN=0 < {IN_PROGRESS, WAITING_PARTS}=1 < RESOLVED=2 < CLOSED=3 — không cho lùi hạng. */
    private static final Map<FeedbackStatus, Integer> HANG = Map.of(
            FeedbackStatus.OPEN, 0,
            FeedbackStatus.IN_PROGRESS, 1,
            FeedbackStatus.WAITING_PARTS, 1,
            FeedbackStatus.RESOLVED, 2,
            FeedbackStatus.CLOSED, 3);

    private final FeedbackRepository feedbackRepo;
    private final MemberRepository memberRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final EquipmentService equipmentService;
    private final ExpenseService expenseService;

    @Transactional
    public FeedbackResponse guiPhanHoi(Long actorUserId, CreateFeedbackRequest req) {
        Member member = memberCuaUser(actorUserId);

        Feedback f = new Feedback();
        f.setMember(member);
        f.setFeedbackType(req.feedbackType());
        f.setDescription(req.description().trim());
        f.setStatus(FeedbackStatus.OPEN);

        switch (req.feedbackType()) {
            case TRAINER -> ganPT(f, req);
            case FACILITY -> ganThietBi(f, req);
            default -> { /* HYGIENE, SERVICE, GENERAL: chỉ vào hàng đợi, không hiệu ứng phụ */ }
        }

        f = feedbackRepo.save(f);
        log.info("Phản hồi #{} ({}) từ hội viên {}: {}",
                f.getId(), f.getFeedbackType(), member.getMemberCode(), f.getDescription());
        return FeedbackResponse.from(f);
    }

    private void ganPT(Feedback f, CreateFeedbackRequest req) {
        if (req.trainerId() == null) {
            throw ApiException.badRequest("TRAINER_REQUIRED", "Vui lòng chọn huấn luyện viên được đánh giá");
        }
        Employee trainer = employeeRepo.findById(req.trainerId())
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy huấn luyện viên"));
        if (!trainer.isTrainer()) {
            throw ApiException.badRequest("NOT_A_TRAINER", "Nhân viên được chọn không phải huấn luyện viên");
        }
        f.setTrainer(trainer);
        f.setRating(req.rating());

        if (req.rating() != null) {
            themDiemPT(trainer, req.rating());
            f.setUrgent(req.rating() <= 2);
        }
    }

    /** Một lượt đánh giá MỚI gia nhập trung bình — tăng cả tổng lẫn số lượt. */
    private void themDiemPT(Employee trainer, int diemMoi) {
        int soCu = trainer.getRatingCount();
        BigDecimal tongCu = trainer.getRatingAvg() != null
                ? trainer.getRatingAvg().multiply(BigDecimal.valueOf(soCu))
                : BigDecimal.ZERO;

        int soMoi = soCu + 1;
        BigDecimal avgMoi = tongCu.add(BigDecimal.valueOf(diemMoi))
                .divide(BigDecimal.valueOf(soMoi), 2, RoundingMode.HALF_UP);

        trainer.setRatingAvg(avgMoi);
        trainer.setRatingCount(soMoi);
        employeeRepo.save(trainer);
        log.info("Cập nhật điểm PT {}: {} sao ({} lượt đánh giá)",
                trainer.getEmployeeCode(), avgMoi, soMoi);
    }

    /** Hội viên SỬA lại một lượt đánh giá đã có — số lượt không đổi, chỉ thay điểm trong tổng. */
    private void suaDiemPT(Employee trainer, int diemCu, int diemMoi) {
        int soLuong = trainer.getRatingCount();
        BigDecimal tongCu = trainer.getRatingAvg().multiply(BigDecimal.valueOf(soLuong));
        BigDecimal tongMoi = tongCu.subtract(BigDecimal.valueOf(diemCu)).add(BigDecimal.valueOf(diemMoi));
        BigDecimal avgMoi = tongMoi.divide(BigDecimal.valueOf(soLuong), 2, RoundingMode.HALF_UP);

        trainer.setRatingAvg(avgMoi);
        employeeRepo.save(trainer);
        log.info("Sửa điểm PT {}: {} sao -> {} sao, trung bình còn {}",
                trainer.getEmployeeCode(), diemCu, diemMoi, avgMoi);
    }

    private void ganThietBi(Feedback f, CreateFeedbackRequest req) {
        if (req.equipmentId() == null) {
            throw ApiException.badRequest("EQUIPMENT_REQUIRED", "Vui lòng chọn thiết bị bị lỗi");
        }
        Equipment equipment = equipmentService.require(req.equipmentId());
        f.setEquipment(equipment);

        if (equipment.getStatus() != EquipmentStatus.RETIRED) {
            equipment.setStatus(EquipmentStatus.NEEDS_REPAIR);
        }
    }

    @Transactional
    public FeedbackResponse capNhatTrangThai(Long feedbackId, Long actorUserId,
                                             FeedbackStatus trangThaiMoi,
                                             String ghiChu, BigDecimal chiPhiSua) {
        Feedback f = require(feedbackId);

        if (HANG.get(trangThaiMoi) < HANG.get(f.getStatus())) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    "Không thể chuyển từ " + f.getStatus() + " lùi về " + trangThaiMoi);
        }

        f.setStatus(trangThaiMoi);
        if (ghiChu != null && !ghiChu.isBlank()) {
            f.setResolutionNote(ghiChu.trim());
        }

        boolean dongXong = trangThaiMoi == FeedbackStatus.RESOLVED || trangThaiMoi == FeedbackStatus.CLOSED;
        if (dongXong && f.getResolvedAt() == null) {
            f.setResolvedAt(OffsetDateTime.now());
            userRepo.findById(actorUserId).ifPresent(f::setResolvedBy);

            if (f.getFeedbackType() == FeedbackType.FACILITY && f.getEquipment() != null) {
                Equipment equipment = f.getEquipment();
                if (equipment.getStatus() != EquipmentStatus.RETIRED) {
                    equipment.setStatus(EquipmentStatus.ACTIVE);
                }

                if (chiPhiSua != null && chiPhiSua.compareTo(BigDecimal.ZERO) > 0) {
                    f.setRepairCost(chiPhiSua);
                    sinhChiPhiSuaChua(equipment, chiPhiSua, f.getId(), actorUserId);
                }
            }
        }

        f = feedbackRepo.save(f);
        log.info("Phản hồi #{} chuyển trạng thái {} -> {}", feedbackId, f.getStatus(), trangThaiMoi);
        return FeedbackResponse.from(f);
    }

    private void sinhChiPhiSuaChua(Equipment equipment, BigDecimal chiPhi, Long feedbackId, Long actorUserId) {
        var req = new CreateExpenseRequest(
                ExpenseCategory.EQUIPMENT_MAINTENANCE,
                "Sửa chữa: " + equipment.getName(),
                chiPhi,
                LocalDate.now(),
                PaymentMethod.BANK_TRANSFER,
                null,
                "Tự sinh từ phản hồi #" + feedbackId);
        expenseService.createExpense(req, actorUserId);
    }

    /**
     * Hội viên tự sửa phản hồi của mình — kể cả sau khi đã được xử lý xong, vì tình huống
     * điển hình là "đánh giá lại số sao cho huấn luyện viên sau khi thấy đã cải thiện".
     * Không cho đổi loại/đối tượng (trainerId, equipmentId) — muốn phản ánh chuyện khác
     * thì gửi phản hồi mới, tránh biến một phản hồi cũ thành nội dung hoàn toàn khác.
     */
    @Transactional
    public FeedbackResponse suaPhanHoi(Long feedbackId, Long actorUserId, UpdateFeedbackRequest req) {
        Feedback f = require(feedbackId);
        Member member = memberCuaUser(actorUserId);

        if (!f.getMember().getId().equals(member.getId())) {
            throw ApiException.forbidden("NOT_OWNER", "Bạn chỉ sửa được phản hồi của chính mình");
        }

        f.setDescription(req.description().trim());

        if (f.getFeedbackType() == FeedbackType.TRAINER && req.rating() != null) {
            Employee trainer = f.getTrainer();
            if (f.getRating() == null) {
                themDiemPT(trainer, req.rating());
            } else if (!f.getRating().equals(req.rating())) {
                suaDiemPT(trainer, f.getRating(), req.rating());
            }
            f.setRating(req.rating());
            f.setUrgent(req.rating() <= 2);
        }

        f = feedbackRepo.save(f);
        log.info("Hội viên {} sửa phản hồi #{}", member.getMemberCode(), feedbackId);
        return FeedbackResponse.from(f);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> phanHoiCuaToi(Long actorUserId) {
        Member member = memberCuaUser(actorUserId);
        return feedbackRepo.findByMemberIdOrderByCreatedAtDesc(member.getId())
                .stream().map(FeedbackResponse::from).toList();
    }

    /**
     * Huấn luyện viên xem lại các đánh giá mà hội viên gửi về mình.
     * Trả rỗng nếu tài khoản không có hồ sơ huấn luyện viên (vd Admin gọi thử) —
     * cùng cách xử lý với {@code PtSessionService.lichDayCuaHuanLuyenVien}.
     */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> phanHoiVeToi(Long actorUserId) {
        User u = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .filter(Employee::isTrainer)
                .map(trainer -> feedbackRepo.findByTrainerIdOrderByCreatedAtDesc(trainer.getId())
                        .stream().map(FeedbackResponse::from).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> hangDoi(FeedbackStatus status, FeedbackType type) {
        List<Feedback> list;
        if (status != null && type != null) {
            list = feedbackRepo.findByStatusAndFeedbackTypeOrderByCreatedAtDesc(status, type);
        } else if (status != null) {
            list = feedbackRepo.findByStatusOrderByCreatedAtDesc(status);
        } else if (type != null) {
            list = feedbackRepo.findByFeedbackTypeOrderByCreatedAtDesc(type);
        } else {
            list = feedbackRepo.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(FeedbackResponse::from).toList();
    }

    private Feedback require(Long id) {
        return feedbackRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phản hồi"));
    }

    private Member memberCuaUser(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .orElseThrow(() -> ApiException.badRequest("NOT_A_MEMBER", "Tài khoản chưa có hồ sơ hội viên"));
    }
}
