package com.gym.training.api.dto;

import com.gym.training.domain.SessionCreditEntry;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sổ cái buổi tập của một hợp đồng.
 *
 * <p>Trả về CẢ số dư hiện tại lẫn toàn bộ lịch sử, để hội viên không chỉ biết
 * "còn mấy buổi" mà còn giải thích được "vì sao còn ngần ấy".
 */
public record SoCaiResponse(
        Long registrationId,
        int soDuHienTai,
        boolean batBienConDung,
        List<ButToan> lichSu
) {
    public record ButToan(
            Long id,
            String entryType,
            int delta,
            int balanceAfter,
            String sourceType,
            Long sourceId,
            String reason,
            OffsetDateTime createdAt
    ) {
        static ButToan from(SessionCreditEntry e) {
            return new ButToan(
                    e.getId(), e.getEntryType().name(), e.getDelta(), e.getBalanceAfter(),
                    e.getSourceType().name(), e.getSourceId(), e.getReason(), e.getCreatedAt());
        }
    }

    public static SoCaiResponse of(Long registrationId, int soDu, boolean batBien,
                                   List<SessionCreditEntry> lichSu) {
        return new SoCaiResponse(registrationId, soDu, batBien,
                lichSu.stream().map(ButToan::from).toList());
    }
}
