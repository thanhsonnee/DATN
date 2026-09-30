package com.gym.sales;

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

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Lễ tân tiếp khách vãng lai cần tư vấn thêm: ghi lại thông tin (tên/SĐT), chọn Sale
 * phụ trách rồi báo sang CRM — lead phải xuất hiện ngay cho Sale, ghi rõ ai đã tạo,
 * và lễ tân KHÔNG được phép tự xem/thao tác toàn bộ phễu CRM (chỉ tạo).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LeTanTaoLeadTest {

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

    private String tokenLeTan, tokenSale;
    private Long saleEmployeeId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        jdbc.execute("SET session_replication_role = replica");
        jdbc.execute("TRUNCATE persons, users, members, employees, registrations, "
                   + "session_credit_ledger, invoices, payments, cash_shifts, "
                   + "revenue_schedules, payroll_runs, payroll_items, expenses, leads "
                   + "RESTART IDENTITY CASCADE");
        jdbc.execute("SET session_replication_role = DEFAULT");

        dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        taoNhanVien("0987654321", "EM-018", "FRONT_DESK", 6_500_000);
        tokenLeTan = dangNhap("0987654321");

        dangKy("Pham Dung Sale", "0900000003");
        doiVaiTro("0900000003", "SALE");
        saleEmployeeId = taoNhanVien("0900000003", "EM-012", "SALES", 7_000_000);
        tokenSale = dangNhap("0900000003");
    }

    @Test
    @DisplayName("Lễ tân tạo lead tại quầy, chọn thẳng Sale phụ trách -> lead ghi rõ người tạo và hiện ngay cho Sale")
    void leTanTaoLead_chonSalePhuTrach_ghiNhanNguoiTao() {
        int leadId = given().header(auth(tokenLeTan))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "fullName", "Khach Vang Lai",
                        "phone", "0977111222",
                        "source", "WALK_IN",
                        "assignedToEmployeeId", saleEmployeeId,
                        "note", "Khach hoi gia goi 3 thang, can Sale tu van them"
                ))
                .when().post("/leads")
                .then().statusCode(201)
                .body("stage", equalTo("NEW"))
                .body("assignedToId", equalTo(saleEmployeeId.intValue()))
                .body("createdByName", equalTo("Vu Thi Mai"))
                .body("createdByRole", equalTo("RECEPTIONIST"))
                .extract().path("id");

        // Sale xem được ngay lead lễ tân vừa tạo, kèm thông tin ai đã tạo
        given().header(auth(tokenSale))
                .when().get("/leads/" + leadId)
                .then().statusCode(200)
                .body("fullName", equalTo("Khach Vang Lai"))
                .body("createdByName", equalTo("Vu Thi Mai"));
    }

    @Test
    @DisplayName("Lead do lễ tân tạo không chọn Sale -> chưa được gán ai, chờ Sale tự nhận")
    void leTanTaoLead_khongChonSale_chuaGanAi() {
        given().header(auth(tokenLeTan))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "fullName", "Khach Chua Ro Sale",
                        "phone", "0977333444",
                        "source", "WALK_IN"
                ))
                .when().post("/leads")
                .then().statusCode(201)
                .body("assignedToId", nullValue());
    }

    @Test
    @DisplayName("Lễ tân KHÔNG được xem toàn bộ phễu CRM -> 403")
    void leTanKhongDuocXemPheu() {
        given().header(auth(tokenLeTan))
                .when().get("/leads")
                .then().statusCode(403);

        given().header(auth(tokenLeTan))
                .when().get("/leads/funnel-stats")
                .then().statusCode(403);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String dangKy(String hoTen, String sdt) {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", hoTen);
        req.put("phone", sdt);
        req.put("password", "MatKhau123@");

        given().contentType(ContentType.JSON).body(req)
                .when().post("/auth/register")
                .then().statusCode(201);

        return dangNhap(sdt);
    }

    private String dangNhap(String sdt) {
        Map<String, Object> req = new HashMap<>();
        req.put("username", sdt);
        req.put("password", "MatKhau123@");

        return given().contentType(ContentType.JSON).body(req)
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private void doiVaiTro(String sdt, String role) {
        jdbc.update("UPDATE users SET primary_role = ? WHERE username = ?", role, sdt);
    }

    private Long taoNhanVien(String sdt, String employeeCode, String department, long baseSalary) {
        Long personId = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = ?", Long.class, sdt);
        return jdbc.queryForObject(
                "INSERT INTO employees (person_id, employee_code, department, start_date, base_salary, status) "
              + "VALUES (?, ?, ?, CURRENT_DATE, ?, 'ACTIVE') RETURNING id", Long.class, personId, employeeCode, department, baseSalary);
    }

    private Header auth(String token) {
        return new Header("Authorization", "Bearer " + token);
    }
}
