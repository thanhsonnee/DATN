package com.gym.checkin.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hàng đợi "hội viên đã bấm check-in trên app, đang chờ lễ tân xác nhận".
 *
 * <p>Cố ý KHÔNG lưu CSDL — đây là tín hiệu tức thời giữa hội viên đứng ở quầy và
 * màn hình lễ tân, không phải dữ liệu nghiệp vụ cần giữ lại. Yêu cầu tự biến mất
 * sau {@link #TTL} nếu lễ tân không xử lý (hội viên bỏ đi, hoặc đã được cho vào
 * qua đường tìm kiếm thủ công) — tránh hàng đợi rác tồn mãi.
 */
@Component
public class PendingSelfCheckInStore {

    private static final Duration TTL = Duration.ofMinutes(3);

    private final Map<Long, OffsetDateTime> yeuCau = new ConcurrentHashMap<>();

    public void themYeuCau(Long memberId) {
        yeuCau.put(memberId, OffsetDateTime.now());
    }

    public void xoaYeuCau(Long memberId) {
        yeuCau.remove(memberId);
    }

    /** Danh sách memberId đang chờ, cũ nhất trước — đã lọc bỏ yêu cầu quá hạn. */
    public List<Long> danhSachDangCho() {
        var hetHan = OffsetDateTime.now().minus(TTL);
        yeuCau.entrySet().removeIf(e -> e.getValue().isBefore(hetHan));

        return yeuCau.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }
}
