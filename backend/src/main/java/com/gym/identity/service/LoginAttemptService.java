package com.gym.identity.service;

import com.gym.identity.domain.User;
import com.gym.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Đếm số lần đăng nhập sai và khóa tạm tài khoản.
 *
 * <p><b>Vì sao phải tách thành một bean riêng:</b> việc ghi nhận lần sai xảy ra ngay
 * trước khi ném ngoại lệ báo sai mật khẩu. Nếu chạy chung giao dịch với
 * {@link AuthService#login}, ngoại lệ sẽ làm giao dịch quay lui và số đếm
 * <b>không bao giờ được lưu</b> — kẻ tấn công có thể thử mật khẩu vô hạn lần.
 *
 * <p>Dùng {@link Propagation#REQUIRES_NEW} để mở một giao dịch độc lập, được ghi
 * nhận kể cả khi giao dịch bên ngoài quay lui. Phải nằm ở bean khác vì Spring
 * không áp dụng được thuộc tính giao dịch khi một phương thức tự gọi chính mình.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    /** Sai quá số lần này thì khóa tạm. Sẽ chuyển sang system_settings khi có bảng cấu hình. */
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        short attempts = (short) (user.getFailedAttempts() + 1);
        user.setFailedAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            // Đặt auto_locked_until — KHÔNG đụng tới locked_until của Admin,
            // để job mở khóa tự động không phá lệnh khóa thủ công.
            user.setAutoLockedUntil(OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Khóa tạm tài khoản do sai mật khẩu {} lần: userId={}", attempts, userId);
        }
        userRepo.save(user);
    }
}
