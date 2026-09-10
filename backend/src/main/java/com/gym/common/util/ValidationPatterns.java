package com.gym.common.util;

/**
 * Các mẫu regex validate dùng chung giữa nhiều DTO, tránh lặp lại rải rác.
 */
public final class ValidationPatterns {

    private ValidationPatterns() {}

    /** SĐT Việt Nam, chấp nhận cả tiền tố 0 và +84 (VD: 0912345678, +84912345678). */
    public static final String PHONE_VN = "^(0|\\+84)[35789][0-9]{8}$";
}
