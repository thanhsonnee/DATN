package com.gym.billing;

import com.gym.billing.domain.Invoice;
import com.gym.billing.domain.InvoiceStatus;
import com.gym.billing.domain.PaymentMethod;
import com.gym.billing.repository.InvoiceRepository;
import com.gym.billing.service.BillingService;
import com.gym.common.exception.ApiException;
import com.gym.identity.domain.Member;
import com.gym.identity.domain.Person;
import com.gym.identity.repository.MemberRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.membership.domain.PackageType;
import com.gym.membership.domain.Registration;
import com.gym.membership.domain.RegistrationStatus;
import com.gym.membership.domain.Membership;
import com.gym.membership.repository.MembershipRepository;
import com.gym.membership.repository.RegistrationRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm chứng nghiệp vụ tiền khi NHIỀU KHOẢN THU / NHIỀU THAO TÁC xảy ra cùng lúc.
 *
 * <p>{@code BillingService.thuTien} đọc số nợ còn lại của hóa đơn rồi mới chặn
 * vượt nợ (OVERPAY) — nếu không khóa dòng hóa đơn, hai khoản thu đồng thời có
 * thể mỗi khoản riêng lẻ đều qua được chặn, dù cộng lại đã vượt số nợ thật.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiaoDichDongThoiTest {

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

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired BillingService billingService;
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired RegistrationRepository registrationRepo;
    @Autowired MembershipRepository membershipRepo;
    @Autowired MemberRepository memberRepo;
    @Autowired PersonRepository personRepo;

    private String tokenLeTan;
    private Long actorId;
    private Registration hopDong;
    private Invoice hoaDon;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        jdbc.execute("SET session_replication_role = replica");
        jdbc.execute("TRUNCATE persons, users, members, employees, registrations, "
                   + "session_credit_ledger, invoices, payments, cash_shifts, check_ins "
                   + "RESTART IDENTITY CASCADE");
        jdbc.execute("SET session_replication_role = DEFAULT");
        for (String seq : new String[]{"member_code_seq", "registration_code_seq",
                                       "invoice_no_seq", "payment_no_seq"}) {
            jdbc.execute("ALTER SEQUENCE " + seq + " RESTART 1");
        }

        dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        taoNhanVien("0987654321", "EM-018", "FRONT_DESK");
        tokenLeTan = dangNhap("0987654321");
        actorId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = '0987654321'", Long.class);

        hopDong = taoHopDong();
        hoaDon = taoHoaDon(hopDong, new BigDecimal("1000000.00"));
    }

    @Test
    @DisplayName("Nhiều khoản thu đồng thời cho cùng hóa đơn: không được thu vượt quá số nợ")
    void thuTienDongThoiKhongDuocVuotNo() throws Exception {
        int soRequest = 10;
        var pool = Executors.newFixedThreadPool(soRequest);
        var batDau = new CountDownLatch(1);
        var thanhCong = new AtomicInteger();
        var vuotNo = new AtomicInteger();

        for (int i = 0; i < soRequest; i++) {
            pool.submit(() -> {
                try {
                    batDau.await();
                    billingService.thuTien(hoaDon.getId(), new BigDecimal("200000"),
                            PaymentMethod.BANK_TRANSFER, actorId, "TT-test");
                    thanhCong.incrementAndGet();
                } catch (ApiException e) {
                    // Vì nợ (1.000.000) chia hết cho mỗi khoản (200.000), khoản thu thứ 6 trở
                    // đi luôn gặp đúng lúc hóa đơn vừa chuyển PAID (dư nợ = 0) — tùy thứ tự khóa
                    // dòng hóa đơn, có thể bị chặn bởi ALREADY_PAID (đã đủ) hoặc OVERPAY (dư nợ
                    // = 0 vẫn còn nhỏ hơn khoản đang thu), cả hai đều đúng nghĩa "bị chặn".
                    if ("OVERPAY".equals(e.getCode()) || "ALREADY_PAID".equals(e.getCode())) {
                        vuotNo.incrementAndGet();
                    }
                } catch (Exception ignored) { }
                return null;
            });
        }
        batDau.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // Nợ 1.000.000, mỗi khoản 200.000 → tối đa đúng 5 khoản thành công
        assertThat(thanhCong.get()).isEqualTo(5);
        assertThat(vuotNo.get()).isEqualTo(5);

        Invoice inv = invoiceRepo.findById(hoaDon.getId()).orElseThrow();
        assertThat(inv.getPaidAmount()).isEqualByComparingTo("1000000.00");
        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // Hợp đồng chỉ được kích hoạt/cấp buổi ĐÚNG MỘT LẦN dù nhiều khoản thu đua nhau
        Integer soLanCapBuoi = jdbc.queryForObject(
                "SELECT COUNT(*) FROM session_credit_ledger "
              + "WHERE registration_id = ? AND entry_type = 'GRANT'",
                Integer.class, hopDong.getId());
        assertThat(soLanCapBuoi).isEqualTo(1);
    }

    @Test
    @DisplayName("Hai lần mở ca đồng thời cho cùng nhân viên: chỉ một ca mở, không lỗi 500")
    void moCaDongThoiKhongLoi500() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var batDau = new CountDownLatch(1);
        var maTrangThai = new CopyOnWriteArrayList<Integer>();

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    batDau.await();
                    int status = given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                            .body(Map.of("openingBalance", 500_000))
                            .when().post("/billing/cash-shifts/open").then().extract().statusCode();
                    maTrangThai.add(status);
                } catch (Exception ignored) { }
                return null;
            });
        }
        batDau.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(maTrangThai).hasSize(2);
        assertThat(maTrangThai).containsExactlyInAnyOrder(201, 409);
    }

    // ==================================================== tiện ích

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
        r.setListPrice(new BigDecimal("1000000.00"));
        r.setDiscountAmount(BigDecimal.ZERO);
        r.setFinalPrice(new BigDecimal("1000000.00"));
        r.setContractDate(LocalDate.now());
        r.setStartDate(LocalDate.now());
        r.setStatus(RegistrationStatus.PENDING_PAYMENT);
        return registrationRepo.save(r);
    }

    private Invoice taoHoaDon(Registration r, BigDecimal soTien) {
        Invoice inv = new Invoice();
        inv.setInvoiceNo("INV-T" + System.nanoTime() % 1_000_000);
        inv.setMember(r.getMember());
        inv.setRegistration(r);
        inv.setTotalAmount(soTien);
        inv.setDueDate(LocalDate.now().plusDays(7));
        return invoiceRepo.save(inv);
    }

    private String dangKy(String hoTen, String sdt) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("fullName", hoTen, "phone", sdt, "password", "MatKhau@2026"))
                .when().post("/auth/register").then().statusCode(201)
                .extract().path("accessToken");
    }

    private String dangNhap(String sdt) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("username", sdt, "password", "MatKhau@2026"))
                .when().post("/auth/login").then().statusCode(200)
                .extract().path("accessToken");
    }

    private void doiVaiTro(String sdt, String vaiTro) {
        jdbc.update("UPDATE users SET primary_role = ? WHERE username = ?", vaiTro, sdt);
    }

    private void taoNhanVien(String sdt, String maNhanVien, String phongBan) {
        Long personId = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = ?", Long.class, sdt);
        jdbc.update("INSERT INTO employees(person_id, employee_code, department, start_date) "
                  + "VALUES (?, ?, ?, CURRENT_DATE)", personId, maNhanVien, phongBan);
    }

    private Header auth(String token) {
        return new Header("Authorization", "Bearer " + token);
    }
}
