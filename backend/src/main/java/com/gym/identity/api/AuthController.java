package com.gym.identity.api;

import com.gym.identity.api.dto.*;
import com.gym.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Xác thực", description = "Đăng ký, đăng nhập, đổi mật khẩu")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Tự đăng ký tài khoản",
            description = "Luôn tạo tài khoản vai trò MEMBER. Chưa tạo hồ sơ hội viên — "
                        + "hồ sơ chỉ sinh ra khi chốt mua gói đầu tiên.")
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @Operation(summary = "Đăng nhập")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "[CHƯA DÙNG] Làm mới access token",
            description = "Access token hết hạn sau 15 phút. Dùng refresh token (hạn 30 ngày) "
                        + "để lấy cặp token mới mà không cần đăng nhập lại. LƯU Ý: frontend "
                        + "hiện CHƯA gọi API này ở đâu cả — khi access token hết hạn (401), "
                        + "người dùng bị đá thẳng về trang đăng nhập thay vì tự làm mới token. "
                        + "Đây là khoảng trống thật, không chỉ là code thừa.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @Operation(summary = "Thông tin tài khoản đang đăng nhập")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Long userId) {
        return authService.me(userId);
    }

    @Operation(summary = "Đổi mật khẩu")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(userId, req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 ký tự trở lên")
            String newPassword
    ) {}
}
