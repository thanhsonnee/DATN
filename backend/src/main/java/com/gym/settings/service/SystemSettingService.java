package com.gym.settings.service;

import com.gym.common.exception.ApiException;
import com.gym.identity.repository.UserRepository;
import com.gym.settings.api.dto.SystemSettingResponse;
import com.gym.settings.domain.SystemSetting;
import com.gym.settings.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Tham số nghiệp vụ chỉnh được qua API (bảng {@code system_settings}), thay cho
 * hằng số hardcode rải rác trong service.
 *
 * <p>Đọc tham số là thao tác rất thường xuyên (mỗi lần huỷ buổi tập, tính
 * lương, quét check-in...), nên giá trị được cache trong bộ nhớ — nạp lại lúc
 * khởi động và cập nhật ngay mỗi khi có thay đổi, không đọc CSDL mỗi lần gọi.
 *
 * <p>Mỗi getter đều nhận kèm giá trị mặc định: nếu khoá chưa được seed hoặc dữ
 * liệu bị nhập sai định dạng, hệ thống dùng mặc định và ghi log cảnh báo, thay
 * vì crash — một tham số cấu hình sai không được phép làm sập nghiệp vụ chính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository repo;
    private final UserRepository userRepo;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void napCache() {
        repo.findAll().forEach(s -> cache.put(s.getSettingKey(), s.getSettingValue()));
        log.info("Đã nạp {} tham số hệ thống vào bộ nhớ", cache.size());
    }

    public int getInt(String key, int macDinh) {
        return parse(key, macDinh, Integer::parseInt);
    }

    public BigDecimal getBigDecimal(String key, BigDecimal macDinh) {
        return parse(key, macDinh, BigDecimal::new);
    }

    public LocalTime getLocalTime(String key, LocalTime macDinh) {
        return parse(key, macDinh, LocalTime::parse);
    }

    private <T> T parse(String key, T macDinh, Function<String, T> convert) {
        String raw = cache.get(key);
        if (raw == null) return macDinh;
        try {
            return convert.apply(raw);
        } catch (Exception ex) {
            log.warn("Tham số '{}' = '{}' sai định dạng, dùng mặc định {}", key, raw, macDinh);
            return macDinh;
        }
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> danhSach() {
        return repo.findAll().stream()
                .sorted((a, b) -> a.getSettingKey().compareTo(b.getSettingKey()))
                .map(SystemSettingResponse::from)
                .toList();
    }

    @Transactional
    public SystemSettingResponse capNhat(String key, String value, Long actorUserId) {
        SystemSetting s = repo.findById(key)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tham số '" + key + "'"));

        s.setSettingValue(value.trim());
        s.setUpdatedAt(OffsetDateTime.now());
        userRepo.findById(actorUserId).ifPresent(s::setUpdatedBy);

        s = repo.save(s);
        cache.put(key, s.getSettingValue());
        log.info("Cập nhật tham số hệ thống '{}' = '{}'", key, s.getSettingValue());
        return SystemSettingResponse.from(s);
    }
}
