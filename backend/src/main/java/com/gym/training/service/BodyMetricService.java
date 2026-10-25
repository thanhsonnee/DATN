package com.gym.training.service;

import com.gym.common.exception.ApiException;
import com.gym.identity.domain.Gender;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;
import com.gym.identity.domain.UserRole;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.membership.repository.MemberTrainerRepository;
import com.gym.training.api.dto.BodyMetricResponse;
import com.gym.training.domain.BodyMetric;
import com.gym.training.repository.BodyMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Chỉ số cơ thể hội viên tự nhập — BMI/BMR/WHR tự tính lúc lưu, hội viên
 * không phải tự tính.
 *
 * <p>BMI dùng ngưỡng châu Á (không phải ngưỡng quốc tế): thiếu cân &lt;18.5,
 * bình thường 18.5–22.9, thừa cân 23–24.9, béo phì ≥25.
 *
 * <p>BMR dùng công thức Mifflin-St Jeor, cần {@code gender}+{@code birthday}
 * từ hồ sơ hội viên — thiếu một trong hai hoặc giới tính {@code OTHER} thì bỏ
 * qua BMR (trả {@code null}) thay vì đoán sai.
 */
@Service
@RequiredArgsConstructor
public class BodyMetricService {

    private final BodyMetricRepository metricRepo;
    private final MemberRepository memberRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final MemberTrainerRepository memberTrainerRepo;

    @Transactional
    public BodyMetricResponse nhapChiSo(Long actorUserId, BigDecimal weightKg, BigDecimal heightCm,
                                         BigDecimal waistCm, BigDecimal hipCm) {
        User u = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        Member m = memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hồ sơ hội viên"));

        BodyMetric metric = new BodyMetric();
        metric.setMember(m);
        metric.setWeightKg(weightKg);
        metric.setHeightCm(heightCm);
        metric.setWaistCm(waistCm);
        metric.setHipCm(hipCm);
        metric.setBmi(tinhBmi(weightKg, heightCm));
        metric.setBmrKcal(tinhBmr(weightKg, heightCm, m.getPerson().getGender(), m.getPerson().getBirthday()));
        metric.setWhr(tinhWhr(waistCm, hipCm));

        metric = metricRepo.save(metric);
        return BodyMetricResponse.from(metric);
    }

    @Transactional(readOnly = true)
    public List<BodyMetricResponse> lichSuCuaToi(Long actorUserId) {
        User u = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return memberRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                .map(m -> metricRepo.findByMemberIdOrderByMeasuredAtAsc(m.getId())
                        .stream().map(BodyMetricResponse::from).toList())
                .orElse(List.of());
    }

    /** Chỉ PT đang được phân công phụ trách hội viên đó, hoặc Admin, mới xem được — chặn IDOR. */
    @Transactional(readOnly = true)
    public List<BodyMetricResponse> lichSuCuaHoiVien(Long actorUserId, Long memberId) {
        User u = userRepo.findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        if (u.getPrimaryRole() != UserRole.ADMIN) {
            boolean dangPhuTrach = employeeRepo.findByPersonIdAndDeletedAtIsNull(u.getPerson().getId())
                    .map(e -> memberTrainerRepo.findByMemberIdAndToDateIsNull(memberId).stream()
                            .anyMatch(mt -> mt.getTrainer().getId().equals(e.getId())))
                    .orElse(false);
            if (!dangPhuTrach) {
                throw ApiException.forbidden("NOT_YOUR_MEMBER",
                        "Bạn không được phân công phụ trách hội viên này");
            }
        }
        return metricRepo.findByMemberIdOrderByMeasuredAtAsc(memberId)
                .stream().map(BodyMetricResponse::from).toList();
    }

    // ---------------------------------------------------------------- công thức

    /** BMI = cân nặng(kg) / chiều cao(m)². */
    private BigDecimal tinhBmi(BigDecimal weightKg, BigDecimal heightCm) {
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP);
    }

    /** WHR = vòng eo / vòng hông. Bỏ qua nếu thiếu một trong hai số đo. */
    private BigDecimal tinhWhr(BigDecimal waistCm, BigDecimal hipCm) {
        if (waistCm == null || hipCm == null) return null;
        return waistCm.divide(hipCm, 3, RoundingMode.HALF_UP);
    }

    /**
     * BMR theo Mifflin-St Jeor. Thiếu giới tính hoặc ngày sinh (hoặc giới tính
     * {@code OTHER}, công thức không phân biệt được) thì trả {@code null} thay
     * vì đoán sai — hội viên tự cập nhật hồ sơ để có số liệu này.
     */
    private Integer tinhBmr(BigDecimal weightKg, BigDecimal heightCm, Gender gender, LocalDate birthday) {
        if (gender == null || birthday == null || gender == Gender.OTHER) return null;

        int tuoi = Period.between(birthday, LocalDate.now()).getYears();
        BigDecimal base = BigDecimal.valueOf(10).multiply(weightKg)
                .add(BigDecimal.valueOf(6.25).multiply(heightCm))
                .subtract(BigDecimal.valueOf(5L * tuoi));

        BigDecimal bmr = gender == Gender.MALE
                ? base.add(BigDecimal.valueOf(5))
                : base.subtract(BigDecimal.valueOf(161));

        return bmr.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
