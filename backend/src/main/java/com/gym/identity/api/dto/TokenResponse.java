package com.gym.identity.api.dto;

/** Kết quả đăng nhập. Không bao giờ chứa mật khẩu hay mã băm mật khẩu. */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        MeResponse user
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, MeResponse user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
