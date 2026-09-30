package com.gym.sales.api.dto;

import com.gym.common.util.ValidationPatterns;
import com.gym.sales.domain.LeadSource;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sửa thông tin chung của Lead khi nhập sai lúc tạo (tên/SĐT/email/nguồn).
 * Khác {@code UpdateLeadContactRequest} (chỉ ghi nhận tương tác/chuyển phễu):
 * DTO này sửa danh tính khách hàng, không đụng tới {@code stage}.
 *
 * <p>Cho sửa {@code phone} vì Lead ở giai đoạn "khách tiềm năng" thường CHƯA có
 * tài khoản đăng nhập gắn với Person — không vướng vấn đề đồng bộ username như
 * sửa SĐT hội viên đã có tài khoản (xem UpdateMemberProfileRequest).
 * Field nào null thì giữ nguyên giá trị cũ.
 */
public record UpdateLeadRequest(
        @Size(max = 150, message = "Họ và tên không quá 150 ký tự")
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE_VN, message = "Số điện thoại phải gồm 10 chữ số hợp lệ (VD: 0912345678)")
        String phone,

        @Size(max = 150, message = "Email không quá 150 ký tự")
        String email,

        LeadSource source,

        Long interestedMembershipId
) {}
