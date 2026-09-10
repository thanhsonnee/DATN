package com.gym.common.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Sinh mật khẩu tạm cho tài khoản do Admin tạo — nhân viên (PT/Sale/Lễ tân/Kế
 * toán) không tự đăng ký được nên không tự đặt mật khẩu như luồng {@code /auth/register}.
 *
 * <p>Loại các ký tự dễ nhầm khi đọc/gõ tay (0/O, 1/l/I) vì Admin phải đọc lại
 * mật khẩu này cho nhân viên nghe — mật khẩu chỉ hiển thị đúng một lần, không
 * gửi qua email hay lưu lại dạng chữ ở bất kỳ đâu.
 */
@Component
public class TempPasswordGenerator {

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
