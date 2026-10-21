package com.gym.training;

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

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Kiểm thử chỉ số cơ thể (nghiệp vụ G3 rút gọn) qua API thật.
 *
 * <p>Trọng tâm: hội viên chỉ nhập số đo thô, BMI/BMR/WHR do service tự tính;
 * và chỉ PT đang được phân công phụ trách (qua {@code member_trainers}) mới
 * xem được chỉ số của hội viên khác — chặn IDOR.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChiSoCoTheTest {

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

    private String tokenHoiVien;
    private Long memberId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        jdbc.execute("SET session_replication_role = replica");
        jdbc.execute("TRUNCATE persons, users, members, employees, registrations, "
                   + "pt_sessions, session_credit_ledger, workout_plans, body_metrics, "
                   + "member_trainers RESTART IDENTITY CASCADE");
        jdbc.execute("SET session_replication_role = DEFAULT");
        jdbc.execute("ALTER SEQUENCE member_code_seq RESTART 1");

        tokenHoiVien = dangKy("Nguyen Van An", "0912345678");

        // Hồ sơ hội viên CHỈ tạo khi chốt mua gói đầu tiên — /auth/register không tạo.
        // Bộ test này không cần luồng mua gói đầy đủ nên tạo thẳng bằng SQL cho gọn.
        Long personId = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = ?", Long.class, "0912345678");
        jdbc.update("INSERT INTO members(person_id, member_code, join_date, status) "
                  + "VALUES (?, 'MB-TEST-01', CURRENT_DATE, 'ACTIVE')", personId);
        memberId = jdbc.queryForObject(
                "SELECT id FROM members WHERE person_id = ?", Long.class, personId);
    }

    @Test
    @DisplayName("Nhập chỉ số thì BMI tự tính đúng công thức, không nhận từ client")
    void nhapChiSoThiTuTinhBmi() {
        float bmiMongDoi = tinhBmi(70, 175); // 70 / 1.75² ≈ 22.86

        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("weightKg", 70, "heightCm", 175))
                .when().post("/body-metrics")
                .then().statusCode(201)
                .body("memberId", equalTo(memberId.intValue()))
                .body("bmi", comparesEqualTo(bmiMongDoi))
                .body("bmrKcal", nullValue()) // chưa có gender/birthday trong hồ sơ
                .body("whr", nullValue());     // không nhập waist/hip
    }

    @Test
    @DisplayName("Đủ giới tính và ngày sinh thì tính đúng BMR theo Mifflin-St Jeor")
    void duThongTinThiTinhBmr() {
        LocalDate ngaySinh = LocalDate.of(2000, 1, 1);
        jdbc.update("UPDATE persons SET gender = 'MALE', birthday = ? "
                  + "WHERE id = (SELECT person_id FROM members WHERE id = ?)", ngaySinh, memberId);

        int tuoi = Period.between(ngaySinh, LocalDate.now()).getYears();
        int bmrMongDoi = Math.round(10f * 70 + 6.25f * 175 - 5f * tuoi + 5f);

        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("weightKg", 70, "heightCm", 175, "waistCm", 80, "hipCm", 100))
                .when().post("/body-metrics")
                .then().statusCode(201)
                .body("bmrKcal", equalTo(bmrMongDoi))
                .body("whr", comparesEqualTo(0.8f));
    }

    @Test
    @DisplayName("Lịch sử của tôi trả về tăng dần theo thời gian")
    void lichSuTangDanTheoThoiGian() {
        nhap(60, 170);
        nhap(61, 170);

        given().header(auth(tokenHoiVien))
                .when().get("/body-metrics/me")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].weightKg", comparesEqualTo(60f))
                .body("[1].weightKg", comparesEqualTo(61f));
    }

    @Test
    @DisplayName("Huấn luyện viên KHÔNG được phân công KHÔNG xem được chỉ số hội viên")
    void ptKhongPhuTrachKhongXemDuoc() {
        nhap(65, 170);

        String tokenPT = dangKy("Tran Binh", "0905111222");
        doiVaiTro("0905111222", "TRAINER");
        taoHoSoNhanVien("0905111222", "EM-005", "TRAINING");
        tokenPT = dangNhap("0905111222");

        given().header(auth(tokenPT))
                .when().get("/body-metrics/member/" + memberId)
                .then().statusCode(403)
                .body("code", equalTo("NOT_YOUR_MEMBER"));
    }

    @Test
    @DisplayName("Huấn luyện viên ĐƯỢC phân công xem được chỉ số hội viên mình phụ trách")
    void ptDuocPhanCongXemDuoc() {
        nhap(65, 170);

        String tokenPT = dangKy("Tran Binh", "0905111222");
        doiVaiTro("0905111222", "TRAINER");
        Long trainerId = taoHoSoNhanVien("0905111222", "EM-005", "TRAINING");
        tokenPT = dangNhap("0905111222");

        jdbc.update("INSERT INTO member_trainers(member_id, trainer_id, from_date) "
                  + "VALUES (?, ?, CURRENT_DATE)", memberId, trainerId);

        given().header(auth(tokenPT))
                .when().get("/body-metrics/member/" + memberId)
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].weightKg", comparesEqualTo(65f));
    }

    // ==================================================== tiện ích

    private float tinhBmi(double weightKg, double heightCm) {
        double heightM = heightCm / 100.0;
        return Math.round((weightKg / (heightM * heightM)) * 100f) / 100f;
    }

    private void nhap(double weightKg, double heightCm) {
        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("weightKg", weightKg, "heightCm", heightCm))
                .when().post("/body-metrics")
                .then().statusCode(201);
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

    private Long taoHoSoNhanVien(String sdt, String maNhanVien, String phongBan) {
        Long personId = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = ?", Long.class, sdt);
        jdbc.update("INSERT INTO employees(person_id, employee_code, department, start_date, level) "
                  + "VALUES (?, ?, ?, CURRENT_DATE, 'SENIOR')", personId, maNhanVien, phongBan);
        return jdbc.queryForObject("SELECT id FROM employees WHERE person_id = ?", Long.class, personId);
    }

    private Header auth(String token) {
        return new Header("Authorization", "Bearer " + token);
    }
}
