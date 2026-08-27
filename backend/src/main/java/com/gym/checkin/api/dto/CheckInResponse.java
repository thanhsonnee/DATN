package com.gym.checkin.api.dto;

import com.gym.checkin.domain.CheckIn;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Kết quả một lượt quét, dùng để dựng màn hình quầy lễ tân.
 *
 * <p>Chứa sẵn thông tin cần cho lễ tân đối chiếu: tên, ảnh hồ sơ, số ngày còn
 * lại của gói. Lễ tân nhìn ảnh, so với người đứng trước mặt, rồi quyết định.
 */
public record CheckInResponse(
        Long id,
        Long memberId,
        String memberCode,
        String memberName,

        /** Key ảnh hồ sơ trong kho lưu trữ. Giao diện đổi thành liên kết tạm để hiển thị. */
        String photoKey,

        String result,
        boolean choPhepVao,
        /** Câu hiển thị to trên màn hình quầy. */
        String thongBao,

        String registrationCode,
        LocalDate endDate,
        Long soNgayConLai,

        String incidentType,
        String incidentNote,
        OffsetDateTime checkedInAt
) {
    public static CheckInResponse from(CheckIn c) {
        boolean choVao = c.getResult().name().startsWith("ALLOWED");

        var reg = c.getRegistration();
        LocalDate hetHan = reg == null ? null : reg.getEndDate();
        Long conLai = hetHan == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), hetHan);

        return new CheckInResponse(
                c.getId(),
                c.getMember().getId(), c.getMember().getMemberCode(),
                c.getMember().getPerson().getFullName(),
                c.getMember().getPerson().getPhotoKey(),
                c.getResult().name(), choVao, thongBaoCho(c.getResult().name(), conLai),
                reg == null ? null : reg.getRegistrationCode(),
                hetHan, conLai,
                c.getIncidentType() == null ? null : c.getIncidentType().name(),
                c.getIncidentNote(),
                c.getCheckedInAt());
    }

    /** Package-visible: dùng lại ở {@link CheckInPreviewResponse} để chữ hiển thị nhất quán. */
    static String thongBaoCho(String result, Long conLai) {
        return switch (result) {
            case "ALLOWED" -> conLai == null
                    ? "Mời vào tập"
                    : "Mời vào tập — còn " + conLai + " ngày";
            case "ALLOWED_OVERRIDE" -> "Đã cho vào dù còn nợ tiền — nhớ nhắc khách thanh toán";
            case "DENIED_EXPIRED"   -> "Gói đã hết hạn — mời khách gia hạn";
            case "DENIED_FROZEN"    -> "Gói đang bảo lưu — cần kết thúc bảo lưu trước";
            case "DENIED_UNPAID"    -> "Còn nợ tiền — thu tiền hoặc bấm cho vào có ghi nhận";
            case "DENIED_SUSPECT"   -> "Hội viên bị hạn chế — yêu cầu xuất trình giấy tờ";
            default -> "Không xác định được, kiểm tra lại";
        };
    }
}
