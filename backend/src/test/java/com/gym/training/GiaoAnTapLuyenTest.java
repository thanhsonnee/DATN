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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Kiểm thử giáo án tập luyện (nghiệp vụ G1 rút gọn) qua API thật.
 *
 * <p>Trọng tâm: nội dung là TEXT TỰ DO (không tách bảng bài tập), sửa giáo án
 * không được xóa lịch sử {@code completedDates} đã đánh dấu, và chỉ đúng
 * người soạn/đúng hội viên mới thao tác được trên giáo án của mình.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiaoAnTapLuyenTest {

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

    private String tokenHoiVien, tokenPT;
    private int registrationId;

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
        jdbc.execute("ALTER SEQUENCE registration_code_seq RESTART 1");

        tokenHoiVien = dangKy("Nguyen Van An", "0912345678");

        tokenPT = dangKy("Tran Binh", "0905111222");
        doiVaiTro("0905111222", "TRAINER");
        taoHoSoNhanVien("0905111222", "EM-005", "TRAINING");
        tokenPT = dangNhap("0905111222");

        registrationId = muaVaKichHoatGoiPT();
    }

    @Test
    @DisplayName("PT soạn giáo án thì hội viên xem được trong danh sách của mình")
    void soanGiaoAnThiHoiVienXemDuoc() {
        int planId = soanGiaoAn("Tuần 1", "Thứ 2/4/6: Ngực-Tay; Thứ 3/5: Chân-Bụng");

        given().header(auth(tokenHoiVien))
                .when().get("/workout-plans/me")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(planId))
                .body("[0].title", equalTo("Tuần 1"))
                .body("[0].trainerName", equalTo("Tran Binh"))
                .body("[0].completedDates", empty());
    }

    @Test
    @DisplayName("Sửa giáo án giữ nguyên lịch sử đã đánh dấu tập")
    void suaGiaoAnGiuNguyenLichSu() {
        int planId = soanGiaoAn("Tuần 1", "Nội dung cũ");

        given().header(auth(tokenHoiVien))
                .when().post("/workout-plans/" + planId + "/log")
                .then().statusCode(200)
                .body("completedDates.size()", equalTo(1));

        given().contentType(ContentType.JSON).header(auth(tokenPT))
                .body(Map.of("title", "Tuần 1 (đã sửa)", "content", "Nội dung mới"))
                .when().put("/workout-plans/" + planId)
                .then().statusCode(200)
                .body("title", equalTo("Tuần 1 (đã sửa)"))
                .body("content", equalTo("Nội dung mới"))
                .body("completedDates.size()", equalTo(1));
    }

    @Test
    @DisplayName("Đánh dấu hai lần cùng một ngày không bị nhân đôi")
    void danhDauTrungNgayKhongNhanDoi() {
        int planId = soanGiaoAn("Tuần 1", "Nội dung");

        given().header(auth(tokenHoiVien))
                .queryParam("date", "2026-01-10")
                .when().post("/workout-plans/" + planId + "/log")
                .then().statusCode(200)
                .body("completedDates.size()", equalTo(1));

        given().header(auth(tokenHoiVien))
                .queryParam("date", "2026-01-10")
                .when().post("/workout-plans/" + planId + "/log")
                .then().statusCode(200)
                .body("completedDates.size()", equalTo(1));
    }

    @Test
    @DisplayName("Huấn luyện viên khác KHÔNG sửa được giáo án không phải của mình")
    void ptKhacKhongSuaDuoc() {
        int planId = soanGiaoAn("Tuần 1", "Nội dung");

        String tokenPtKhac = dangKy("Le Cuong", "0933444555");
        doiVaiTro("0933444555", "TRAINER");
        taoHoSoNhanVien("0933444555", "EM-009", "TRAINING");
        tokenPtKhac = dangNhap("0933444555");

        given().contentType(ContentType.JSON).header(auth(tokenPtKhac))
                .body(Map.of("title", "Chiếm giáo án", "content", "..."))
                .when().put("/workout-plans/" + planId)
                .then().statusCode(403)
                .body("code", equalTo("NOT_YOUR_PLAN"));
    }

    @Test
    @DisplayName("Hội viên khác KHÔNG đánh dấu được giáo án không phải của mình")
    void hoiVienKhacKhongDanhDauDuoc() {
        int planId = soanGiaoAn("Tuần 1", "Nội dung");

        String tokenNguoiKhac = dangKy("Pham Dung", "0944555666");

        given().header(auth(tokenNguoiKhac))
                .when().post("/workout-plans/" + planId + "/log")
                .then().statusCode(403)
                .body("code", equalTo("NOT_YOUR_PLAN"));
    }

    // ==================================================== tiện ích

    private int soanGiaoAn(String title, String content) {
        return given().contentType(ContentType.JSON).header(auth(tokenPT))
                .body(Map.of("registrationId", registrationId, "title", title, "content", content))
                .when().post("/workout-plans")
                .then().statusCode(201)
                .extract().path("id");
    }

    private int muaVaKichHoatGoiPT() {
        Long idGoi = jdbc.queryForObject(
                "SELECT id FROM memberships WHERE code = 'PT-12'", Long.class);

        int id = given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("membershipId", idGoi))
                .when().post("/registrations")
                .then().statusCode(201).extract().path("id");

        String tokenLeTan = dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        tokenLeTan = dangNhap("0987654321");

        given().header(auth(tokenLeTan))
                .when().post("/registrations/" + id + "/activate")
                .then().statusCode(200);
        return id;
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

    private void taoHoSoNhanVien(String sdt, String maNhanVien, String phongBan) {
        Long personId = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = ?", Long.class, sdt);
        jdbc.update("INSERT INTO employees(person_id, employee_code, department, start_date, level) "
                  + "VALUES (?, ?, ?, CURRENT_DATE, 'SENIOR')", personId, maNhanVien, phongBan);
    }

    private Header auth(String token) {
        return new Header("Authorization", "Bearer " + token);
    }
}
