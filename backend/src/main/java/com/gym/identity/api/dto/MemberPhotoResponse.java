package com.gym.identity.api.dto;

public record MemberPhotoResponse(
        Long memberId,
        String memberCode,
        String fullName,
        String photoKey,
        String photoUrl
) {}
