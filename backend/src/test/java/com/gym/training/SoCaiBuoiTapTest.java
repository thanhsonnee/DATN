package com.gym.training;

import com.gym.common.exception.ApiException;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.Person;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.membership.domain.*;
import com.gym.membership.repository.MembershipRepository;
import com.gym.membership.repository.RegistrationRepository;
import com.gym.training.domain.LedgerEntryType;
import com.gym.training.domain.SessionCreditEntry;
import com.gym.training.repository.SessionCreditRepository;
import com.gym.training.service.SessionCreditLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Kiểm chứng SỔ CÁI TÍN DỤNG BUỔI TẬP — đóng góp học thuật số 1.
 *
 * <p>Chạy trên PostgreSQL thật vì phần lớn cơ chế bảo vệ nằm ở tầng cơ sở dữ liệu:
 * trigger chặn sửa xóa, khóa dòng chống ghi đồng thời, ràng buộc số học. Cơ sở dữ
 * liệu giả lập trong bộ nhớ sẽ bỏ qua hết và cho kết quả sai lệch.
 *
 * <h3>Ba bất biến được kiểm chứng</h3>
 * <ol>
 *   <li>Tổng delta == số dư của bút toán cuối</li>
 *   <li>Số dư không bao giờ âm</li>
 *   <li>Số dư sau == số dư trước + delta, ở MỌI bút toán</li>
 * </ol>
 */
@Testcontainers
@SpringBootTest
class SoCaiBuoiTapTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gymdb_test").withUsername("gym").withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.jwt.secret", () -> "chuoi-bi-mat-danh-rieng-cho-kiem-thu-du-32-ky-tu");
    }

    @Autowired SessionCreditLedgerService soCai;
    @Autowired SessionCreditRepository ledgerRepo;
    @Autowired RegistrationRepository registrationRepo;
    @Autowired MembershipRepository membershipRepo;
    @Autowired MemberRepository memberRepo;
    @Autowired PersonRepository personRepo;
    @Autowired JdbcTemplate jdbc;

    private Registration hopDong;

    @BeforeEach
    void setUp() {
        // Trigger chặn xóa nên phải tạm tắt ràng buộc mới dọn được sổ cái
        jdbc.execute("SET session_replication_role = replica");
        jdbc.execute("TRUNCATE persons, members, registrations, session_credit_ledger "
                   + "RESTART IDENTITY CASCADE");
        jdbc.execute("SET session_replication_role = DEFAULT");

        hopDong = taoHopDong();
    }

    // ==================================================== bất biến cơ bản

    @Test
    @DisplayName("Cấp buổi rồi tiêu dần: số dư giảm đúng từng bước")
    void capBuoiRoiTieuDan() {
        soCai.capBuoi(hopDong, 12);
        assertThat(soCai.soDuHienTai(hopDong.getId())).isEqualTo(12);

        soCai.truBuoi(hopDong, 1001L);
        soCai.truBuoi(hopDong, 1002L);
        soCai.truBuoi(hopDong, 1003L);

        assertThat(soCai.soDuHienTai(hopDong.getId())).isEqualTo(9);
        assertThat(soCai.batBienConDung(hopDong.getId())).isTrue();
    }

    @Test
    @DisplayName("BẤT BIẾN 2: không thể tiêu quá số buổi đang có")
    void khongTheTieuQuaSoDu() {
        soCai.capBuoi(hopDong, 2);
        soCai.truBuoi(hopDong, 1001L);
        soCai.truBuoi(hopDong, 1002L);

        assertThatThrownBy(() -> soCai.truBuoi(hopDong, 1003L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Không đủ số buổi");

        assertThat(soCai.soDuHienTai(hopDong.getId())).isZero();
    }

    @Test
    @DisplayName("Một buổi tập chỉ được trừ buổi ĐÚNG MỘT LẦN")
    void khongTruHaiLanChoCungMotBuoi() {
        soCai.capBuoi(hopDong, 12);
        soCai.truBuoi(hopDong, 5001L);

        assertThatThrownBy(() -> soCai.truBuoi(hopDong, 5001L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã được trừ buổi rồi");

        assertThat(soCai.soDuHienTai(hopDong.getId())).isEqualTo(11);
    }

    @Test
    @DisplayName("Mỗi hợp đồng chỉ được cấp buổi một lần")
    void khongCapBuoiHaiLan() {
        soCai.capBuoi(hopDong, 12);
        assertThatThrownBy(() -> soCai.capBuoi(hopDong, 5))
                .isInstanceOf(Exception.class);
    }

    // ==================================================== tính chất chỉ ghi thêm

    @Test
    @DisplayName("Bút toán đã ghi thì KHÔNG sửa được, kể cả bằng lệnh SQL trực tiếp")
    void khongSuaDuocButToan() {
        soCai.capBuoi(hopDong, 12);
        Long id = ledgerRepo.findByRegistrationIdOrderByIdAsc(hopDong.getId()).get(0).getId();

        assertThatThrownBy(() ->
                jdbc.update("UPDATE session_credit_ledger SET delta = 999 WHERE id = ?", id))
                .hasMessageContaining("chi duoc GHI THEM");
    }

    @Test
    @DisplayName("Bút toán đã ghi thì KHÔNG xóa được, kể cả bằng lệnh SQL trực tiếp")
    void khongXoaDuocButToan() {
        soCai.capBuoi(hopDong, 12);
        Long id = ledgerRepo.findByRegistrationIdOrderByIdAsc(hopDong.getId()).get(0).getId();

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM session_credit_ledger WHERE id = ?", id))
                .hasMessageContaining("chi duoc GHI THEM");
    }

    @Test
    @DisplayName("Sửa sai bằng bút toán ADJUST, vết cũ vẫn giữ nguyên")
    void suaSaiBangButToanMoi() {
        soCai.capBuoi(hopDong, 12);
        soCai.truBuoi(hopDong, 1001L);        // trừ nhầm

        Long adminId = taoUser();
        soCai.dieuChinh(hopDong, 1, "Trừ nhầm buổi ngày 20/08, hoàn lại cho hội viên", adminId);

        assertThat(soCai.soDuHienTai(hopDong.getId())).isEqualTo(12);

        // Lịch sử giữ ĐỦ 3 bút toán — không có gì bị xóa đi
        List<SessionCreditEntry> lichSu = soCai.lichSu(hopDong.getId());
        assertThat(lichSu).hasSize(3)
                .extracting(SessionCreditEntry::getEntryType)
                .containsExactly(LedgerEntryType.GRANT, LedgerEntryType.CONSUME,
                                 LedgerEntryType.ADJUST);
    }

    @Test
    @DisplayName("Điều chỉnh thủ công bắt buộc ghi lý do")
    void dieuChinhPhaiCoLyDo() {
        soCai.capBuoi(hopDong, 12);
        Long adminId = taoUser();

        assertThatThrownBy(() -> soCai.dieuChinh(hopDong, -1, "  ", adminId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("bắt buộc ghi lý do");
    }

    // ==================================================== loại biến động đặc biệt

    @Test
    @DisplayName("Hết hạn thu hồi hết số buổi còn dư")
    void hetHanThuHoiBuoiConDu() {
        soCai.capBuoi(hopDong, 12);
        soCai.truBuoi(hopDong, 1001L);
        soCai.truBuoi(hopDong, 1002L);

        soCai.thuHoiBuoiHetHan(hopDong);

        assertThat(soCai.soDuHienTai(hopDong.getId())).isZero();
        assertThat(soCai.batBienConDung(hopDong.getId())).isTrue();

        // Đây là biến động KHÔNG tương ứng buổi tập nào — lý do sổ cái phải là
        // bảng riêng, không thể suy ra bằng cách đếm pt_sessions
        assertThat(soCai.lichSu(hopDong.getId()))
                .last()
                .extracting(SessionCreditEntry::getDelta)
                .isEqualTo(-10);
    }

    @Test
    @DisplayName("Hủy đúng hạn thì buổi được hoàn lại")
    void huyDungHanHoanBuoi() {
        soCai.capBuoi(hopDong, 12);
        soCai.truBuoi(hopDong, 1001L);
        soCai.hoanBuoi(hopDong, 1001L, "Hội viên hủy trước 4 tiếng");

        assertThat(soCai.soDuHienTai(hopDong.getId())).isEqualTo(12);
    }

    // ==================================================== property-based

    @RepeatedTest(value = 100, name = "Chuỗi thao tác ngẫu nhiên #{currentRepetition}")
    @DisplayName("BẤT BIẾN 1 và 3 đúng với mọi chuỗi thao tác ngẫu nhiên")
    void batBienDungVoiMoiChuoiNgauNhien() {
        Random rnd = new Random();
        int soBuoiBanDau = 5 + rnd.nextInt(45);

        soCai.capBuoi(hopDong, soBuoiBanDau);

        Long adminId = taoUser();
        long nguonId = 10_000L;

        // Sinh chuỗi thao tác trộn lẫn 4 loại, mỗi loại đều hợp lệ về nghiệp vụ
        for (int i = 0; i < 30; i++) {
            int soDuTruoc = soCai.soDuHienTai(hopDong.getId());
            try {
                switch (rnd.nextInt(4)) {
                    case 0 -> soCai.truBuoi(hopDong, ++nguonId);
                    case 1 -> soCai.hoanBuoi(hopDong, ++nguonId, "Hủy đúng hạn");
                    case 2 -> soCai.dieuChinh(hopDong, rnd.nextBoolean() ? 1 : -1,
                                              "Điều chỉnh ngẫu nhiên trong kiểm thử", adminId);
                    case 3 -> { /* bỏ lượt, mô phỏng khoảng lặng */ }
                }
            } catch (ApiException ex) {
                // Thao tác bị từ chối vì không đủ buổi là hành vi ĐÚNG.
                // Điều quan trọng: số dư phải giữ nguyên như trước khi thử.
                assertThat(soCai.soDuHienTai(hopDong.getId()))
                        .as("Thao tác bị từ chối không được làm đổi số dư")
                        .isEqualTo(soDuTruoc);
            }
        }

        kiemChungBaBatBien(hopDong.getId());
    }

    @Test
    @DisplayName("BẤT BIẾN 3: mọi bút toán đều có số dư sau = số dư trước + delta")
    void chuoiSoDuLienTuc() {
        soCai.capBuoi(hopDong, 20);
        Long adminId = taoUser();
        for (int i = 0; i < 10; i++) soCai.truBuoi(hopDong, 2000L + i);
        soCai.dieuChinh(hopDong, 3, "Tặng thêm buổi khuyến mãi", adminId);
        soCai.thuHoiBuoiHetHan(hopDong);

        kiemChungBaBatBien(hopDong.getId());
    }

    // ==================================================== xử lý đồng thời

    @Test
    @DisplayName("20 request cùng trừ buổi một lúc: số dư vẫn đúng, không trừ dư")
    void truBuoiDongThoi() throws Exception {
        soCai.capBuoi(hopDong, 12);

        int soRequest = 20;
        var pool = Executors.newFixedThreadPool(8);
        var batDau = new CountDownLatch(1);
        var thanhCong = new AtomicInteger();
        var thatBai = new AtomicInteger();

        for (int i = 0; i < soRequest; i++) {
            final long nguonId = 7000L + i;
            pool.submit(() -> {
                try {
                    batDau.await();
                    soCai.truBuoi(hopDong, nguonId);
                    thanhCong.incrementAndGet();
                } catch (Exception e) {
                    thatBai.incrementAndGet();
                }
                return null;
            });
        }

        batDau.countDown();                       // thả cả 20 request cùng lúc
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // Chỉ có 12 buổi nên đúng 12 request được, 8 request phải bị từ chối
        assertThat(thanhCong.get()).isEqualTo(12);
        assertThat(thatBai.get()).isEqualTo(8);
        assertThat(soCai.soDuHienTai(hopDong.getId())).isZero();

        kiemChungBaBatBien(hopDong.getId());
    }

    // ==================================================== tiện ích

    /** Kiểm tra cả ba bất biến trên toàn bộ lịch sử của một hợp đồng. */
    private void kiemChungBaBatBien(Long registrationId) {
        List<SessionCreditEntry> lichSu = soCai.lichSu(registrationId);

        int tongDelta = 0;
        int soDuTruoc = 0;

        for (SessionCreditEntry e : lichSu) {
            // BẤT BIẾN 3
            assertThat(e.getBalanceAfter())
                    .as("Bút toán #%d: số dư sau phải bằng %d + (%d)",
                        e.getId(), soDuTruoc, e.getDelta())
                    .isEqualTo(soDuTruoc + e.getDelta());

            // BẤT BIẾN 2
            assertThat(e.getBalanceAfter())
                    .as("Bút toán #%d: số dư không được âm", e.getId())
                    .isNotNegative();

            tongDelta += e.getDelta();
            soDuTruoc = e.getBalanceAfter();
        }

        // BẤT BIẾN 1
        if (!lichSu.isEmpty()) {
            assertThat(tongDelta)
                    .as("Tổng delta phải khớp số dư của bút toán cuối")
                    .isEqualTo(lichSu.get(lichSu.size() - 1).getBalanceAfter());
        }
        assertThat(soCai.batBienConDung(registrationId)).isTrue();
    }

    private Registration taoHopDong() {
        Person p = new Person();
        p.setFullName("Nguyen Van An");
        p.setPhone("09" + (10_000_000 + new Random().nextInt(89_999_999)));
        p = personRepo.save(p);

        Member m = new Member();
        m.setPerson(p);
        m.setMemberCode("MB-T" + System.nanoTime() % 100_000);
        m.setJoinDate(LocalDate.now());
        m = memberRepo.save(m);

        Membership pkg = membershipRepo.findByCodeAndDeletedAtIsNull("PT-12").orElseThrow();

        Registration r = new Registration();
        r.setRegistrationCode("REG-T" + System.nanoTime() % 1_000_000);
        r.setMember(m);
        r.setMembership(pkg);
        r.setPackageType(PackageType.SESSION_BASED);
        r.setSessionsTotal(12);
        r.setListPrice(new BigDecimal("4200000.00"));
        r.setDiscountAmount(BigDecimal.ZERO);
        r.setFinalPrice(new BigDecimal("4200000.00"));
        r.setContractDate(LocalDate.now());
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusDays(180));
        r.setStatus(RegistrationStatus.ACTIVE);
        return registrationRepo.save(r);
    }

    private Long taoUser() {
        jdbc.update("INSERT INTO users(person_id, username, password_hash, primary_role) "
                  + "VALUES (?, ?, 'x', 'ADMIN') ON CONFLICT DO NOTHING",
                    1L, "admin-test");
        return jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'admin-test'", Long.class);
    }
}
