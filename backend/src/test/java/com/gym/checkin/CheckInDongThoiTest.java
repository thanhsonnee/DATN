package com.gym.checkin;

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

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Kiểm chứng check-in khi NHIỀU LỄ TÂN / NHIỀU THIẾT BỊ thao tác cùng lúc.
 *
 * <p>{@code CheckInService.quetVao} đọc "đang có lượt mở cùng ngày chưa" rồi
 * mới ghi lượt mới — nếu không khóa dòng hội viên, hai lượt quét gần như đồng
 * thời cho cùng một người (hai đầu đọc thẻ, hoặc bấm đúp) đều có thể đọc
 * "chưa có lượt mở" trước khi lượt kia kịp ghi, khiến cả hai cùng ALLOWED.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckInDongThoiTest {

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

    private String tokenHoiVien, tokenLeTan;
    private int registrationId, memberId;

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

        tokenHoiVien = dangKy("Nguyen Van An", "0912345678");

        dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        taoNhanVien("0987654321", "EM-018", "FRONT_DESK");
        tokenLeTan = dangNhap("0987654321");

        registrationId = muaGoi("PT-12");
        memberId = jdbc.queryForObject("SELECT id FROM members LIMIT 1", Integer.class);
    }

    @Test
    @DisplayName("Nhiều lượt quét vào đồng thời cho cùng một hội viên: chỉ MỘT được cho vào")
    void quetVaoDongThoiChoCungHoiVien() throws Exception {
        kichHoatHopDong();

        int soRequest = 8;
        var pool = Executors.newFixedThreadPool(soRequest);
        var batDau = new CountDownLatch(1);
        var ketQua = new CopyOnWriteArrayList<String>();

        for (int i = 0; i < soRequest; i++) {
            pool.submit(() -> {
                try {
                    batDau.await();
                    String result = given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                            .body(Map.of("memberId", memberId))
                            .when().post("/check-ins")
                            .then().statusCode(200).extract().path("result");
                    ketQua.add(result);
                } catch (Exception ignored) { }
                return null;
            });
        }
        batDau.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(ketQua).hasSize(soRequest);
        assertThat(ketQua).filteredOn("ALLOWED"::equals).hasSize(1);
        assertThat(ketQua).filteredOn("DENIED_ALREADY_INSIDE"::equals).hasSize(soRequest - 1);

        Integer soLuotDangMo = jdbc.queryForObject(
                "SELECT COUNT(*) FROM check_ins WHERE member_id = ? AND checked_out_at IS NULL "
              + "  AND result IN ('ALLOWED','ALLOWED_OVERRIDE')",
                Integer.class, memberId);
        assertThat(soLuotDangMo).isEqualTo(1);
    }

    @Test
    @DisplayName("Hội viên gửi yêu cầu tự check-in đúng lúc lễ tân quét thẻ thủ công: " +
                "hàng đợi không được 'sống lại' sau khi đã xử lý xong")
    void guiYeuCauDuaVoiQuetTheThuCong() throws Exception {
        kichHoatHopDong();

        var pool = Executors.newFixedThreadPool(2);
        var batDau = new CountDownLatch(1);

        Future<Integer> quet = pool.submit(() -> {
            batDau.await();
            return given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                    .body(Map.of("memberId", memberId))
                    .when().post("/check-ins").then().extract().statusCode();
        });
        Future<Integer> tuRequest = pool.submit(() -> {
            batDau.await();
            return given().header(auth(tokenHoiVien))
                    .when().post("/check-ins/self-request").then().extract().statusCode();
        });

        batDau.countDown();
        assertThat(quet.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(tuRequest.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        pool.shutdown();

        // Hội viên đã có lượt vào thật (do quét thẻ) — hàng đợi màn hình quầy KHÔNG
        // được còn treo yêu cầu này, kẻo lễ tân tưởng vẫn phải xử lý một yêu cầu đã xong.
        given().header(auth(tokenLeTan)).when().get("/check-ins/pending-requests")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @DisplayName("Hội viên bấm 'Tôi đã đến' nhiều lần liên tiếp: hàng đợi không bị nhân bản")
    void guiYeuCauTuCheckInNhieuLanLienTiep() throws Exception {
        int soRequest = 6;
        var pool = Executors.newFixedThreadPool(soRequest);
        var batDau = new CountDownLatch(1);
        var maTrangThai = new CopyOnWriteArrayList<Integer>();

        for (int i = 0; i < soRequest; i++) {
            pool.submit(() -> {
                try {
                    batDau.await();
                    int status = given().header(auth(tokenHoiVien))
                            .when().post("/check-ins/self-request").then().extract().statusCode();
                    maTrangThai.add(status);
                } catch (Exception ignored) { }
                return null;
            });
        }
        batDau.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(maTrangThai).hasSize(soRequest).allMatch(s -> s == 200);

        given().header(auth(tokenLeTan)).when().get("/check-ins/pending-requests")
                .then().statusCode(200).body("size()", equalTo(1));
    }

    // ==================================================== tiện ích

    private void moCa(int tienDauCa) {
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("openingBalance", tienDauCa))
                .when().post("/billing/cash-shifts/open").then().statusCode(201);
    }

    private int xuatHoaDon() {
        return jdbc.queryForObject(
                "SELECT id FROM invoices WHERE registration_id = ?", Integer.class, registrationId);
    }

    private void thuTien(int invoiceId, int soTien, String hinhThuc) {
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("invoiceId", invoiceId, "amount", soTien, "method", hinhThuc))
                .when().post("/billing/payments").then().statusCode(201);
    }

    private void kichHoatHopDong() {
        moCa(0);
        thuTien(xuatHoaDon(), 4_200_000, "CASH");
    }

    private int muaGoi(String maGoi) {
        Long idGoi = jdbc.queryForObject(
                "SELECT id FROM memberships WHERE code = ?", Long.class, maGoi);
        return given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("membershipId", idGoi))
                .when().post("/registrations").then().statusCode(201)
                .extract().path("id");
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
