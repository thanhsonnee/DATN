package com.gym.identity.api.dto;

import com.gym.identity.domain.Member;
import com.gym.identity.domain.User;

/** Thông tin tài khoản trả về cho giao diện. */
public record MeResponse(
        Long userId,
        String username,
        String fullName,
        String phone,
        String role,
        String status,
        Long memberId,
        String memberCode,
        boolean isMember
) {
    public static MeResponse from(User u, Member m) {
        return new MeResponse(
                u.getId(),
                u.getUsername(),
                u.getPerson().getFullName(),
                u.getPerson().getPhone(),
                u.getPrimaryRole().name(),
                u.getStatus().name(),
                m == null ? null : m.getId(),
                m == null ? null : m.getMemberCode(),
                m != null);
    }
}
