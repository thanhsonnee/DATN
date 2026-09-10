package com.gym.common.config;

import com.gym.membership.domain.Membership;
import com.gym.membership.domain.PackageType;
import com.gym.membership.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Nạp bảng giá mẫu khi CSDL còn trống, để chạy lên là demo được ngay.
 *
 * <p>Không dùng migration Flyway vì đây là dữ liệu minh họa, không phải cấu trúc.
 * Chỉ chạy khi bảng gói tập rỗng nên không bao giờ ghi đè dữ liệu thật.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DemoDataSeeder {

    private final MembershipRepository membershipRepo;

    @Bean
    public ApplicationRunner seedMemberships() {
        return args -> {
            if (membershipRepo.count() > 0) return;

            membershipRepo.saveAll(java.util.List.of(
                    pkg("DAY-01", "Vé tập 1 ngày", PackageType.DAY_PASS, 1, null, 80_000, 0, 1),
                    pkg("FIT-01M", "Gói Fitness 1 tháng", PackageType.TIME_BASED, 30, null, 700_000, 0, 2),
                    pkg("FIT-03M", "Gói Fitness 3 tháng", PackageType.TIME_BASED, 90, null, 1_800_000, 14, 3),
                    pkg("FIT-06M", "Gói Fitness 6 tháng", PackageType.TIME_BASED, 180, null, 3_000_000, 30, 4),
                    pkg("FIT-12M", "Gói Fitness 12 tháng", PackageType.TIME_BASED, 365, null, 4_800_000, 30, 5),
                    ptPkg("PT-12", "Gói PT 12 buổi", 90, 12, 4_200_000, 14, 6),
                    ptPkg("PT-24", "Gói PT 24 buổi", 180, 24, 7_680_000, 30, 7),
                    combo()));

            log.info("Đã nạp {} gói tập mẫu", membershipRepo.count());
        };
    }

    private Membership pkg(String code, String name, PackageType type, Integer days,
                           Integer sessions, long price, int freezeDays, int order) {
        Membership m = new Membership();
        m.setCode(code);
        m.setName(name);
        m.setPackageType(type);
        m.setDurationDays(days);
        m.setSessionCount(sessions);
        m.setPrice(BigDecimal.valueOf(price));
        m.setMaxFreezeDays(freezeDays);
        m.setIsRefundable(freezeDays > 0);
        m.setDisplayOrder(order);
        return m;
    }

    private Membership ptPkg(String code, String name, int days, int sessions, long price,
                             int freezeDays, int order) {
        Membership m = pkg(code, name, PackageType.SESSION_BASED, days, sessions, price, freezeDays, order);
        m.setIncludesTrainer(true);
        return m;
    }

    /** Gói kết hợp BẮT BUỘC khai tỷ lệ giá trị phần PT, nếu không CSDL sẽ từ chối. */
    private Membership combo() {
        Membership m = pkg("COMBO-12M-PT24", "Combo 12 tháng + PT 24 buổi",
                PackageType.HYBRID, 365, 24, 11_500_000, 30, 8);
        m.setIncludesTrainer(true);
        m.setPtValueRatio(new BigDecimal("0.700"));
        return m;
    }
}
