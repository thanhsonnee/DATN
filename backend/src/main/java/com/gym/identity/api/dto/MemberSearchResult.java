package com.gym.identity.api.dto;

import com.gym.identity.domain.Member;

/** Một dòng kết quả tìm kiếm hội viên — đủ thông tin để lễ tân nhận ra đúng người. */
public record MemberSearchResult(
        Long memberId,
        String memberCode,
        String fullName,
        String phone,
        String status,
        String photoKey
) {
    public static MemberSearchResult from(Member m) {
        return new MemberSearchResult(
                m.getId(), m.getMemberCode(), m.getPerson().getFullName(),
                m.getPerson().getPhone(), m.getStatus().name(),
                m.getPerson().getPhotoKey());
    }
}
