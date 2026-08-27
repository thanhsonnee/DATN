package com.gym.checkin.api.dto;

import com.gym.checkin.domain.CheckInResult;
import com.gym.identity.domain.Member;
import com.gym.membership.domain.Registration;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Xem trước tình trạng hội viên — KHÔNG ghi lượt check-in nào.
 *
 * <p>Cùng đúng logic xét điều kiện với lúc quét thật ({@code CheckInService.quetVao}),
 * chỉ khác là không lưu gì cả. Lễ tân chọn hội viên từ ô tìm kiếm là thấy ngay
 * tình trạng này TRƯỚC khi bấm "Xác nhận vào tập", thay vì phải bấm mù rồi mới
 * biết kết quả.
 */
public record CheckInPreviewResponse(
        Long memberId,
        String memberCode,
        String memberName,
        String photoKey,

        String result,
        boolean choPhepVao,
        String thongBao,

        String registrationCode,
        LocalDate endDate,
        Long soNgayConLai
) {
    public static CheckInPreviewResponse of(Member m, CheckInResult result, Registration reg) {
        boolean choVao = result.name().startsWith("ALLOWED");

        LocalDate hetHan = reg == null ? null : reg.getEndDate();
        Long conLai = hetHan == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), hetHan);

        return new CheckInPreviewResponse(
                m.getId(), m.getMemberCode(), m.getPerson().getFullName(), m.getPerson().getPhotoKey(),
                result.name(), choVao, CheckInResponse.thongBaoCho(result.name(), conLai),
                reg == null ? null : reg.getRegistrationCode(), hetHan, conLai);
    }
}
