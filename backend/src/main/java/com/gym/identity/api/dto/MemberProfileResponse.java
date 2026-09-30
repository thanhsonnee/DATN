package com.gym.identity.api.dto;

import com.gym.identity.domain.Gender;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.MemberGoal;
import com.gym.identity.domain.MemberSource;

import java.time.LocalDate;

/** Hồ sơ đầy đủ của một hội viên — dùng để hiển thị/nạp sẵn form sửa. */
public record MemberProfileResponse(
        Long memberId,
        String memberCode,
        String phone,
        String fullName,
        Gender gender,
        LocalDate birthday,
        String nationalId,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        String healthNote,
        MemberGoal goal,
        MemberSource source
) {
    public static MemberProfileResponse from(Member m) {
        var p = m.getPerson();
        return new MemberProfileResponse(
                m.getId(), m.getMemberCode(), p.getPhone(), p.getFullName(),
                p.getGender(), p.getBirthday(), p.getNationalId(), p.getEmail(), p.getAddress(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(),
                m.getHealthNote(), m.getGoal(), m.getSource());
    }
}
