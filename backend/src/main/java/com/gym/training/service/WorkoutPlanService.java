package com.gym.training.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.common.exception.ApiException;
import com.gym.identity.domain.User;
import com.gym.identity.domain.UserRole;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.domain.Registration;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.training.api.dto.WorkoutPlanResponse;
import com.gym.training.domain.WorkoutPlan;
import com.gym.training.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Giáo án tập luyện — PT soạn text tự do cho hội viên, hội viên đánh dấu ngày
 * đã tập.
 *
 * <p>Đơn giản hóa nghiệp vụ G: không tách bảng bài tập/sets/reps, không AI
 * sinh nháp — PT tự viết, tự chịu trách nhiệm nội dung.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutPlanService {

    private final WorkoutPlanRepository planRepo;
    private final RegistrationRepository registrationRepo;
    private final MemberRepository memberRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public WorkoutPlanResponse soanGiaoAn(Long actorUserId, Long registrationId, String title, String content) {
        User u = nguoiDung(actorUserId);
        var trainer = employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .filter(e -> e.isTrainer())
                .orElseThrow(() -> ApiException.forbidden("NOT_A_TRAINER",
                        "Chỉ huấn luyện viên mới soạn được giáo án"));

        Registration hopDong = registrationRepo.findById(registrationId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hợp đồng"));

        WorkoutPlan p = new WorkoutPlan();
        p.setRegistration(hopDong);
        p.setTrainer(trainer);
        p.setTitle(title);
        p.setContent(content);
        p.setCompletedDates("[]");

        p = planRepo.save(p);
        log.info("PT {} soạn giáo án #{} cho hội viên {}",
                trainer.getEmployeeCode(), p.getId(), hopDong.getMember().getMemberCode());
        return toResponse(p);
    }

    /** Sửa title/content — KHÔNG đụng tới {@code completedDates}, giữ nguyên lịch sử đã đánh dấu. */
    @Transactional
    public WorkoutPlanResponse suaGiaoAn(Long actorUserId, Long planId, String title, String content) {
        WorkoutPlan p = require(planId);
        phaiLaNguoiSoanGiaoAn(p, actorUserId);

        p.setTitle(title);
        p.setContent(content);
        return toResponse(p);
    }

    @Transactional
    public WorkoutPlanResponse danhDauDaTap(Long actorUserId, Long planId, LocalDate ngay) {
        WorkoutPlan p = require(planId);
        phaiLaHoiVienCuaGiaoAn(p, actorUserId);

        LocalDate ngayDanhDau = ngay == null ? LocalDate.now() : ngay;
        var ds = new TreeSet<>(doc(p.getCompletedDates()));
        ds.add(ngayDanhDau.toString());
        p.setCompletedDates(ghi(new ArrayList<>(ds)));
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<WorkoutPlanResponse> giaoAnCuaToi(Long actorUserId) {
        User u = nguoiDung(actorUserId);
        return memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(m -> planRepo.findByRegistrationMemberIdOrderByCreatedAtDesc(m.getId())
                        .stream().map(this::toResponse).toList())
                .orElse(List.of());
    }

    // ---------------------------------------------------------------- riêng tư

    private WorkoutPlan require(Long id) {
        return planRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giáo án"));
    }

    private User nguoiDung(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    /** Không cho huấn luyện viên khác sửa hộ giáo án không phải của mình. */
    private void phaiLaNguoiSoanGiaoAn(WorkoutPlan p, Long actorUserId) {
        User u = nguoiDung(actorUserId);
        if (u.getPrimaryRole() == UserRole.ADMIN) return;

        boolean dungNguoi = employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(e -> e.getId().equals(p.getTrainer().getId()))
                .orElse(false);
        if (!dungNguoi) {
            throw ApiException.forbidden("NOT_YOUR_PLAN", "Bạn không phải người soạn giáo án này");
        }
    }

    /** Không cho hội viên khác đánh dấu hộ giáo án không phải của mình. */
    private void phaiLaHoiVienCuaGiaoAn(WorkoutPlan p, Long actorUserId) {
        User u = nguoiDung(actorUserId);
        if (u.getPrimaryRole() == UserRole.ADMIN) return;

        boolean dungNguoi = p.getRegistration().getMember().getPerson().getId().equals(u.getPerson().getId());
        if (!dungNguoi) {
            throw ApiException.forbidden("NOT_YOUR_PLAN", "Đây không phải giáo án của bạn");
        }
    }

    private List<String> doc(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String ghi(List<String> ds) {
        try {
            return objectMapper.writeValueAsString(ds);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private WorkoutPlanResponse toResponse(WorkoutPlan p) {
        return WorkoutPlanResponse.from(p, doc(p.getCompletedDates()));
    }
}
