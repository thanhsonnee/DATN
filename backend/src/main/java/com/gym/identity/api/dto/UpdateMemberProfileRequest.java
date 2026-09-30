package com.gym.identity.api.dto;

import com.gym.identity.domain.Gender;
import com.gym.identity.domain.MemberGoal;
import com.gym.identity.domain.MemberSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Sửa hồ sơ hội viên — Lễ tân/Admin. KHÔNG có {@code phone}: SĐT là username
 * đăng nhập, đổi cần đồng bộ riêng (chưa làm — xem ghi chú ở luồng CRM Lead).
 * Cũng không có memberCode/joinDate/status/photoKey/cardUid/lastVisitAt — các
 * field này do hệ thống quản lý hoặc có luồng nghiệp vụ riêng (ảnh có API
 * riêng, cấm hội viên là hành động riêng, không phải field sửa tự do).
 */
public record UpdateMemberProfileRequest(
        @Size(max = 150, message = "Họ tên không quá 150 ký tự")
        String fullName,

        Gender gender,

        LocalDate birthday,

        @Size(max = 20, message = "CCCD/CMND không quá 20 ký tự")
        String nationalId,

        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email không quá 150 ký tự")
        String email,

        @Size(max = 255, message = "Địa chỉ không quá 255 ký tự")
        String address,

        @Size(max = 150, message = "Tên người liên hệ khẩn cấp không quá 150 ký tự")
        String emergencyContactName,

        @Size(max = 20, message = "SĐT người liên hệ khẩn cấp không quá 20 ký tự")
        String emergencyContactPhone,

        String healthNote,

        MemberGoal goal,

        MemberSource source
) {}
