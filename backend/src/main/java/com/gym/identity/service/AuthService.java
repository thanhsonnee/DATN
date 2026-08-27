package com.gym.identity.service;

import com.gym.common.exception.ApiException;
import com.gym.identity.api.dto.*;
import com.gym.identity.domain.*;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.identity.repository.UserRepository;
import com.gym.identity.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** Đăng ký, đăng nhập và đổi mật khẩu. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PersonRepository personRepo;
    private final UserRepository userRepo;
    private final MemberRepository memberRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Hội viên tự đăng ký trên app.
     *
     * <p>Chỉ tạo {@link Person} + {@link User}. KHÔNG tạo {@link Member} — hồ sơ
     * hội viên chỉ sinh ra khi chốt mua gói đầu tiên (phương án A).
     */
    @Transactional
    public TokenResponse register(RegisterRequest req) {

        if (personRepo.existsByPhoneAndDeletedAtIsNull(req.phone())) {
            throw ApiException.conflict("PHONE_TAKEN", "Số điện thoại này đã được đăng ký");
        }
        if (req.email() != null && !req.email().isBlank()
                && personRepo.existsByEmailAndDeletedAtIsNull(req.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "Email này đã được sử dụng");
        }
        if (userRepo.existsByUsernameAndDeletedAtIsNull(req.phone())) {
            throw ApiException.conflict("USERNAME_TAKEN", "Tài khoản này đã tồn tại");
        }

        Person person = new Person();
        person.setFullName(req.fullName().trim());
        person.setPhone(req.phone());
        person.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
        person = personRepo.save(person);

        User user = new User();
        user.setPerson(person);
        user.setUsername(req.phone());          // số điện thoại là định danh chính ở Việt Nam
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPrimaryRole(UserRole.MEMBER);   // GÁN CỨNG — không bao giờ lấy từ client
        user.setStatus(UserStatus.ACTIVE);
        user = userRepo.save(user);

        log.info("Tài khoản mới: userId={} phone={}", user.getId(), req.phone());
        return buildToken(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {

        User user = userRepo.findByUsernameAndDeletedAtIsNull(req.username())
                // Không tiết lộ tài khoản có tồn tại hay không — chống dò tài khoản
                .orElseThrow(() -> ApiException.unauthorized(
                        "BAD_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw ApiException.forbidden("ACCOUNT_LOCKED",
                    "Tài khoản đang bị khóa. Lý do: " + user.getLockedReason());
        }
        if (!user.canLogin()) {
            throw ApiException.forbidden("TEMPORARILY_LOCKED",
                    "Tài khoản tạm khóa do nhập sai mật khẩu nhiều lần. Vui lòng thử lại sau.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // Phải ghi ở GIAO DỊCH RIÊNG: ném ngoại lệ ngay sau đó sẽ làm giao dịch
            // hiện tại quay lui, và số lần sai sẽ không bao giờ được lưu lại.
            loginAttemptService.recordFailure(user.getId());
            throw ApiException.unauthorized("BAD_CREDENTIALS",
                    "Tên đăng nhập hoặc mật khẩu không đúng");
        }

        user.setFailedAttempts((short) 0);
        user.setAutoLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());

        return buildToken(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {

        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "Mật khẩu mới phải khác mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        log.info("Đổi mật khẩu: userId={}", userId);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        return MeResponse.from(user, findMember(user));
    }

    /**
     * Cấp access token mới từ refresh token còn hạn, không bắt đăng nhập lại.
     *
     * <p>Vai trò/trạng thái luôn đọc lại từ CSDL tại thời điểm gọi — không tin
     * vào claim cũ trong refresh token — nên tài khoản bị khóa sau khi đăng
     * nhập sẽ bị chặn ngay ở lần refresh kế tiếp, không phải chờ 15 phút.
     */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Long userId = Long.valueOf(jwtService.parseRefreshToken(refreshToken).getSubject());

        User user = userRepo.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.unauthorized(
                        "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ hoặc đã hết hạn"));

        if (!user.canLogin()) {
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản đang bị khóa");
        }

        return buildToken(user);
    }

    // ---------------------------------------------------------------- riêng tư

    private TokenResponse buildToken(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = jwtService.issueRefreshToken(user);
        MeResponse me = MeResponse.from(user, findMember(user));
        return TokenResponse.of(accessToken, refreshToken, jwtService.accessTokenSeconds(), me);
    }

    /** Trả về hồ sơ hội viên nếu người này đã mua gói, ngược lại trả {@code null}. */
    private Member findMember(User user) {
        return memberRepo.findByPersonIdAndDeletedAtIsNull(user.getPerson().getId()).orElse(null);
    }
}
