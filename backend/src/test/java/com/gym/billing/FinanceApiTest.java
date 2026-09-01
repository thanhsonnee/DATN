package com.gym.billing;

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
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinanceApiTest {

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

    private String tokenAdmin, tokenKeToan, tokenLeTan, tokenHLV;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        jdbc.execute("SET session_replication_role = replica");
        jdbc.execute("TRUNCATE persons, users, members, employees, registrations, "
                   + "session_credit_ledger, invoices, payments, cash_shifts, "
                   + "revenue_schedules, payroll_runs, payroll_items, expenses "
                   + "RESTART IDENTITY CASCADE");
        jdbc.execute("SET session_replication_role = DEFAULT");

        // Tạo Admin
        dangKy("Admin User", "0900000001");
        doiVaiTro("0900000001", "ADMIN");
        tokenAdmin = dangNhap("0900000001");

        // Tạo Kế toán
        dangKy("Le Thi Hoa", "0966777888");
        doiVaiTro("0966777888", "ACCOUNTANT");
        taoNhanVien("0966777888", "EM-021", "ACCOUNTING", 9_000_000);
        tokenKeToan = dangNhap("0966777888");

        // Tạo Lễ tân
        dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        taoNhanVien("0987654321", "EM-018", "FRONT_DESK", 6_500_000);
        tokenLeTan = dangNhap("0987654321");

        // Tạo HLV
        dangKy("Tran Van PT", "0911222333");
        doiVaiTro("0911222333", "TRAINER");
        taoNhanVien("0911222333", "EM-007", "TRAINING", 8_000_000);
        tokenHLV = dangNhap("0911222333");
    }

    // =========================================================================
    // E3: Ghi nhận doanh thu dồn tích
    // =========================================================================

    @Test
    @DisplayName("E3: Mua gói & thanh toán tự động sinh lịch phân bổ doanh thu dồn tích")
    void e3_revenueRecognitionReport() {
        int month = LocalDate.now().getMonthValue();
        int year = LocalDate.now().getYear();

        // Hội viên tự mua gói
        String tokenHoiVien = dangKy("Nguyen Van An", "0912345678");
        int regId = muaGoi(tokenHoiVien, "FIT-06M");

        // Lễ tân xác nhận gói tập
        given().header(auth(tokenLeTan))
                .contentType(ContentType.JSON)
                .body(Map.of("method", "BANK_TRANSFER"))
                .when().post("/billing/registrations/" + regId + "/confirm")
                .then().statusCode(200);

        // Kế toán kiểm tra báo cáo doanh thu
        given().header(auth(tokenKeToan))
                .queryParam("month", month)
                .queryParam("year", year)
                .when().get("/finance/revenue/report")
                .then().statusCode(200)
                .body("month", equalTo(month))
                .body("year", equalTo(year))
                .body("totalCashCollected", greaterThan(0f))
                .body("schedules", hasSize(greaterThan(0)));
    }

    // =========================================================================
    // E4: Tính và chạy bảng lương
    // =========================================================================

    @Test
    @DisplayName("E4: Tính toán bảng lương, chỉnh sửa thưởng phạt và duyệt chi trả")
    void e4_payrollFlow() {
        int month = LocalDate.now().getMonthValue();
        int year = LocalDate.now().getYear();

        // 1. Tính toán bảng lương
        int payrollId = given().header(auth(tokenKeToan))
                .contentType(ContentType.JSON)
                .body(Map.of("periodMonth", month, "periodYear", year))
                .when().post("/finance/payrolls/calculate")
                .then().statusCode(201)
                .body("status", equalTo("DRAFT"))
                .body("items", hasSize(3))
                .extract().path("id");

        // 2. Lấy chi tiết bảng lương
        int itemId = given().header(auth(tokenKeToan))
                .when().get("/finance/payrolls/" + payrollId)
                .then().statusCode(200)
                .extract().path("items[0].id");

        // 3. Kế toán thưởng thêm cho nhân viên
        given().header(auth(tokenKeToan))
                .contentType(ContentType.JSON)
                .body(Map.of("bonusAmount", 500_000, "deductionAmount", 0, "note", "Thưởng nhân viên xuất sắc"))
                .when().put("/finance/payrolls/items/" + itemId)
                .then().statusCode(200)
                .body("bonusAmount", equalTo(500000.0f));

        // 4. Admin duyệt bảng lương
        given().header(auth(tokenAdmin))
                .when().post("/finance/payrolls/" + payrollId + "/approve")
                .then().statusCode(200)
                .body("status", equalTo("APPROVED"));

        // 5. Kế toán xác nhận đã chi trả
        given().header(auth(tokenKeToan))
                .when().post("/finance/payrolls/" + payrollId + "/pay")
                .then().statusCode(200)
                .body("status", equalTo("PAID"));

        // 6. HLV xem phiếu lương cá nhân của mình
        given().header(auth(tokenHLV))
                .queryParam("month", month)
                .queryParam("year", year)
                .when().get("/finance/payrolls/my-payslip")
                .then().statusCode(200)
                .body("employeeCode", equalTo("EM-007"))
                .body("baseSalary", equalTo(8000000.0f));
    }

    // =========================================================================
    // E5: Quản lý Chi phí & Báo cáo Lợi nhuận P&L
    // =========================================================================

    @Test
    @DisplayName("E5: Ghi nhận chi phí và xem báo cáo kết quả kinh doanh P&L")
    void e5_expenseAndProfitLossReport() {
        int month = LocalDate.now().getMonthValue();
        int year = LocalDate.now().getYear();

        // 1. Ghi nhận chi phí tiền điện
        given().header(auth(tokenKeToan))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "category", "UTILITIES",
                        "title", "Tiền điện tháng " + month,
                        "amount", 12_000_000,
                        "spentAt", LocalDate.now().toString(),
                        "paymentMethod", "BANK_TRANSFER"
                ))
                .when().post("/finance/expenses")
                .then().statusCode(201)
                .body("category", equalTo("UTILITIES"))
                .body("amount", equalTo(12000000.0f));

        // 2. Tra cứu danh sách chi phí
        given().header(auth(tokenKeToan))
                .when().get("/finance/expenses")
                .then().statusCode(200)
                .body("$", hasSize(1));

        // 3. Xem báo cáo Lợi nhuận P&L
        given().header(auth(tokenKeToan))
                .queryParam("month", month)
                .queryParam("year", year)
                .when().get("/finance/profit-loss")
                .then().statusCode(200)
                .body("operatingExpenses", equalTo(12000000.0f))
                .body("expensesByCategory.UTILITIES", equalTo(12000000.0f));
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
                .extract().path("token");
    }

    private void doiVaiTro(String sdt, String role) {
        jdbc.update("UPDATE users SET primary_role = ? WHERE username = ?", role, sdt);
    }

    private void taoNhanVien(String sdt, String employeeCode, String department, long baseSalary) {
        Long personId = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = ?", Long.class, sdt);
        jdbc.update("INSERT INTO employees (person_id, employee_code, department, start_date, base_salary, status) "
                  + "VALUES (?, ?, ?, CURRENT_DATE, ?, 'ACTIVE')", personId, employeeCode, department, baseSalary);
    }

    private int muaGoi(String token, String packageCode) {
        Long pkgId = jdbc.queryForObject(
                "SELECT id FROM memberships WHERE code = ?", Long.class, packageCode);
        return given().header(auth(token))
                .contentType(ContentType.JSON)
                .body(Map.of("membershipId", pkgId))
                .when().post("/registrations")
                .then().statusCode(201)
                .extract().path("id");
    }

    private Header auth(String token) {
        return new Header("Authorization", "Bearer " + token);
    }
}
