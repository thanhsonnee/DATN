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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LeadApiTest {

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

    private String tokenAdmin, tokenSale, tokenLeTan;
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

        // Tạo Admin
        dangKy("Admin User", "0900000001");
        doiVaiTro("0900000001", "ADMIN");
        tokenAdmin = dangNhap("0900000001");

        // Tạo Sale
        dangKy("Pham Dung Sale", "0900000003");
        doiVaiTro("0900000003", "SALE");
        saleEmployeeId = taoNhanVien("0900000003", "EM-012", "SALES", 7_000_000);
        tokenSale = dangNhap("0900000003");

        // Tạo Lễ tân
        dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        taoNhanVien("0987654321", "EM-018", "FRONT_DESK", 6_500_000);
        tokenLeTan = dangNhap("0987654321");
    }

    @Test
    @DisplayName("F1: Khách gửi form tư vấn công khai -> Lead ở trạng thái NEW")
    void f1_publicLeadCreation() {
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "fullName", "Trần Khách Lạ",
                        "phone", "0988111222",
                        "email", "khachla@gmail.com",
                        "note", "Tôi muốn đăng ký tập thử Yoga"
                ))
                .when().post("/leads/public")
                .then().statusCode(201)
                .body("fullName", equalTo("Trần Khách Lạ"))
                .body("source", equalTo("WEB_FORM"))
                .body("stage", equalTo("NEW"));
    }

    @Test
    @DisplayName("F2: Toàn bộ vòng đời phễu bán hàng (NEW -> CONTACTED -> TRIAL -> WON / LOST)")
    void f2_fullSalesFunnel() {
        // 1. Tạo Lead nội bộ
        int leadId = given().header(auth(tokenSale))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "fullName", "Nguyễn Tiềm Năng",
                        "phone", "0912888999",
                        "source", "HOTLINE",
                        "note", "Khách gọi hỏi gói 6 tháng",
                        "nextFollowUp", LocalDate.now().plusDays(2).toString()
                ))
                .when().post("/leads")
                .then().statusCode(201)
                .body("stage", equalTo("NEW"))
                .extract().path("id");

        // 2. Phân công Sale phụ trách
        given().header(auth(tokenSale))
                .contentType(ContentType.JSON)
                .body(Map.of("employeeId", saleEmployeeId))
                .when().put("/leads/" + leadId + "/assign")
                .then().statusCode(200)
                .body("assignedToId", equalTo(saleEmployeeId.intValue()));

        // 3. Sale gọi điện & đặt lịch tập thử
        given().header(auth(tokenSale))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "stage", "TRIAL_BOOKED",
                        "contactNote", "Đã tư vấn, hẹn 18h ngày mai đến trải nghiệm phòng tập",
                        "nextFollowUp", LocalDate.now().plusDays(1).toString()
                ))
                .when().put("/leads/" + leadId + "/contact")
                .then().statusCode(200)
                .body("stage", equalTo("TRIAL_BOOKED"));

        // 4. Lấy thống kê phễu bán hàng
        given().header(auth(tokenSale))
                .when().get("/leads/funnel-stats")
                .then().statusCode(200)
                .body("totalLeads", equalTo(1))
                .body("stageCounts.TRIAL_BOOKED", equalTo(1));

        // 5. Khách chốt hợp đồng tại quầy -> Lead TỰ ĐỘNG chuyển sang WON
        Long pkgId = jdbc.queryForObject("SELECT id FROM memberships WHERE code = 'FIT-06M'", Long.class);
        given().header(auth(tokenLeTan))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "fullName", "Nguyễn Tiềm Năng",
                        "phone", "0912888999",
                        "membershipId", pkgId,
                        "payNow", true,
                        "paymentMethod", "CASH"
                ))
                .when().post("/registrations/desk")
                .then().statusCode(201);

        // Kiểm tra Lead đã chuyển sang WON
        given().header(auth(tokenSale))
                .when().get("/leads/" + leadId)
                .then().statusCode(200)
                .body("stage", equalTo("WON"));
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
