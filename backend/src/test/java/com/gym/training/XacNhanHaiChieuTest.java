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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Kiểm thử XÁC NHẬN HAI CHIỀU qua API thật — cơ chế chống khai khống buổi tập.
 *
 * <p>Trọng tâm là chứng minh: buổi tập chỉ được tính công và trừ buổi khi cả
 * huấn luyện viên và hội viên cùng xác nhận. Thiếu một bên thì không có gì xảy ra.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class XacNhanHaiChieuTest {

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

    private String tokenHoiVien, tokenPT, tokenLeTan;
    private int registrationId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        jdbc.execute("SET session_replication_role = replica");
        jdbc.execute("TRUNCATE persons, users, members, employees, registrations, "
                   + "pt_sessions, session_credit_ledger RESTART IDENTITY CASCADE");
        jdbc.execute("SET session_replication_role = DEFAULT");
        jdbc.execute("ALTER SEQUENCE member_code_seq RESTART 1");
        jdbc.execute("ALTER SEQUENCE registration_code_seq RESTART 1");

        tokenHoiVien = dangKy("Nguyen Van An", "0912345678");

        tokenPT = dangKy("Tran Binh", "0905111222");
        doiVaiTro("0905111222", "TRAINER");
        taoHoSoNhanVien("0905111222", "EM-005", "TRAINING");
        tokenPT = dangNhap("0905111222");

        tokenLeTan = dangKy("Vu Thi Mai", "0987654321");
        doiVaiTro("0987654321", "RECEPTIONIST");
        tokenLeTan = dangNhap("0987654321");

        registrationId = muaVaKichHoatGoiPT();
    }

    // ==================================================== luồng đầy đủ

    @Test
    @DisplayName("Kích hoạt hợp đồng gói PT thì sổ cái được cấp đủ số buổi")
    void kichHoatThiCapBuoi() {
        given().header(auth(tokenHoiVien))
                .when().get("/registrations/" + registrationId + "/credit-ledger")
                .then().statusCode(200)
                .body("soDuHienTai", equalTo(12))
                .body("batBienConDung", equalTo(true))
                .body("lichSu[0].entryType", equalTo("GRANT"))
                .body("lichSu[0].delta", equalTo(12));
    }

    @Test
    @DisplayName("Đủ hai xác nhận thì buổi hoàn thành và sổ cái trừ đúng một buổi")
    void duHaiXacNhanThiTruBuoi() {
        int sessionId = datLichVaDuyet();

        given().header(auth(tokenPT))
                .when().post("/pt-sessions/" + sessionId + "/trainer-confirm")
                .then().statusCode(200)
                .body("status", equalTo("SCHEDULED"))          // chưa hoàn thành
                .body("trainerConfirmedAt", notNullValue())
                .body("memberConfirmedAt", nullValue());

        assertThat(soDu()).as("Chưa đủ hai xác nhận thì CHƯA trừ buổi").isEqualTo(12);

        given().header(auth(tokenHoiVien))
                .when().post("/pt-sessions/" + sessionId + "/member-confirm")
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("autoConfirmed", equalTo(false));

        assertThat(soDu()).isEqualTo(11);
    }

    // ==================================================== chống khai khống

    @Test
    @DisplayName("CHỈ huấn luyện viên xác nhận thì KHÔNG trừ buổi, không tính công")
    void chiPtXacNhanThiChuaTinh() {
        int sessionId = datLichVaDuyet();

        given().header(auth(tokenPT))
                .when().post("/pt-sessions/" + sessionId + "/trainer-confirm")
                .then().statusCode(200);

        assertThat(soDu()).isEqualTo(12);
        assertThat(trangThai(sessionId)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("Hội viên KHÔNG xác nhận được khi huấn luyện viên chưa bấm kết thúc")
    void hoiVienKhongXacNhanTruoc() {
        int sessionId = datLichVaDuyet();

        given().header(auth(tokenHoiVien))
                .when().post("/pt-sessions/" + sessionId + "/member-confirm")
                .then().statusCode(400)
                .body("code", equalTo("TRAINER_NOT_CONFIRMED"));

        assertThat(soDu()).isEqualTo(12);
    }

    @Test
    @DisplayName("Huấn luyện viên khác KHÔNG xác nhận hộ buổi của người khác được")
    void ptKhacKhongXacNhanHo() {
        int sessionId = datLichVaDuyet();

        String tokenPtKhac = dangKy("Le Cuong", "0933444555");
        doiVaiTro("0933444555", "TRAINER");
        taoHoSoNhanVien("0933444555", "EM-009", "TRAINING");
        tokenPtKhac = dangNhap("0933444555");

        given().header(auth(tokenPtKhac))
                .when().post("/pt-sessions/" + sessionId + "/trainer-confirm")
                .then().statusCode(403)
                .body("code", equalTo("NOT_YOUR_SESSION"));

        assertThat(soDu()).isEqualTo(12);
    }

    @Test
    @DisplayName("Hội viên khác KHÔNG xác nhận hộ buổi của người khác được")
    void hoiVienKhacKhongXacNhanHo() {
        int sessionId = datLichVaDuyet();
        given().header(auth(tokenPT)).when().post("/pt-sessions/" + sessionId + "/trainer-confirm");

        String tokenNguoiKhac = dangKy("Pham Dung", "0944555666");

        given().header(auth(tokenNguoiKhac))
                .when().post("/pt-sessions/" + sessionId + "/member-confirm")
                .then().statusCode(403)
                .body("code", equalTo("NOT_YOUR_SESSION"));

        assertThat(soDu()).isEqualTo(12);
    }

    @Test
    @DisplayName("Xác nhận hai lần cũng chỉ trừ đúng một buổi")
    void xacNhanHaiLanChiTruMotBuoi() {
        int sessionId = datLichVaDuyet();
        given().header(auth(tokenPT)).when().post("/pt-sessions/" + sessionId + "/trainer-confirm");
        given().header(auth(tokenHoiVien)).when().post("/pt-sessions/" + sessionId + "/member-confirm");

        assertThat(soDu()).isEqualTo(11);

        // Bấm lại lần nữa phải bị từ chối
        given().header(auth(tokenHoiVien))
                .when().post("/pt-sessions/" + sessionId + "/member-confirm")
                .then().statusCode(anyOf(is(400), is(409)));

        assertThat(soDu()).isEqualTo(11);
    }

    // ==================================================== đặt lịch

    @Test
    @DisplayName("Huấn luyện viên từ chối thì phải nêu lý do")
    void tuChoiPhaiNeuLyDo() {
        int sessionId = datLich();

        given().contentType(ContentType.JSON).header(auth(tokenPT))
                .body(Map.of("reason", ""))
                .when().post("/pt-sessions/" + sessionId + "/reject")
                .then().statusCode(400);

        given().contentType(ContentType.JSON).header(auth(tokenPT))
                .body(Map.of("reason", "Trung lich voi hoc vien khac"))
                .when().post("/pt-sessions/" + sessionId + "/reject")
                .then().statusCode(200)
                .body("status", equalTo("REJECTED"));
    }

    @Test
    @DisplayName("Hết buổi thì không đặt lịch mới được")
    void hetBuoiKhongDatLich() {
        // Điều chỉnh sổ cái về 0 bằng quyền quản trị viên
        String tokenAdmin = dangKy("Quan tri", "0999888777");
        doiVaiTro("0999888777", "ADMIN");
        tokenAdmin = dangNhap("0999888777");

        given().contentType(ContentType.JSON).header(auth(tokenAdmin))
                .body(Map.of("delta", -12, "reason", "Dua ve 0 de kiem thu"))
                .when().post("/registrations/" + registrationId + "/credit-ledger/adjust")
                .then().statusCode(200)
                .body("soDuHienTai", equalTo(0));

        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(bodyDatLich())
                .when().post("/pt-sessions")
                .then().statusCode(409)
                .body("code", equalTo("NO_CREDIT_LEFT"));
    }

    // ==================================================== hủy và vắng mặt

    @Test
    @DisplayName("Hội viên vắng mặt thì mất buổi")
    void hoiVienVangMatThiMatBuoi() {
        int sessionId = datLichVaDuyet();

        given().contentType(ContentType.JSON).header(auth(tokenPT))
                .body(Map.of("noShowBy", "MEMBER", "note", "Goi dien khong nghe may"))
                .when().post("/pt-sessions/" + sessionId + "/no-show")
                .then().statusCode(200)
                .body("status", equalTo("NO_SHOW"))
                .body("noShowBy", equalTo("MEMBER"));

        assertThat(soDu()).isEqualTo(11);
    }

    @Test
    @DisplayName("Huấn luyện viên vắng mặt thì hội viên KHÔNG mất buổi")
    void ptVangMatThiHoiVienKhongMatBuoi() {
        int sessionId = datLichVaDuyet();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("noShowBy", "TRAINER", "note", "PT bao om dot xuat"))
                .when().post("/pt-sessions/" + sessionId + "/no-show")
                .then().statusCode(200)
                .body("noShowBy", equalTo("TRAINER"));

        assertThat(soDu()).as("Lỗi thuộc về PT, không được lấy buổi của hội viên")
                .isEqualTo(12);
    }

    @Test
    @DisplayName("Hủy sớm không mất buổi, và ghi nhận đúng là không hủy muộn")
    void huySomKhongMatBuoi() {
        int sessionId = datLichVaDuyet();

        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("cancelledBy", "MEMBER", "reason", "Ban dot xuat"))
                .when().post("/pt-sessions/" + sessionId + "/cancel")
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("isLateCancel", equalTo(false));

        assertThat(soDu()).isEqualTo(12);
    }

    // ==================================================== sổ cái công khai

    @Test
    @DisplayName("Sổ cái trả về đủ lịch sử để giải thích vì sao còn ngần ấy buổi")
    void soCaiGiaiThichDuocSoDu() {
        int s1 = datLichVaDuyet();
        given().header(auth(tokenPT)).when().post("/pt-sessions/" + s1 + "/trainer-confirm");
        given().header(auth(tokenHoiVien)).when().post("/pt-sessions/" + s1 + "/member-confirm");

        given().header(auth(tokenHoiVien))
                .when().get("/registrations/" + registrationId + "/credit-ledger")
                .then().statusCode(200)
                .body("soDuHienTai", equalTo(11))
                .body("lichSu.size()", equalTo(2))
                .body("lichSu[0].entryType", equalTo("GRANT"))
                .body("lichSu[0].balanceAfter", equalTo(12))
                .body("lichSu[1].entryType", equalTo("CONSUME"))
                .body("lichSu[1].balanceAfter", equalTo(11))
                // Bút toán trừ buổi truy ngược được về đúng buổi tập nào
                .body("lichSu[1].sourceType", equalTo("PT_SESSION"))
                .body("lichSu[1].sourceId", equalTo(s1));
    }

    @Test
    @DisplayName("Chỉ quản trị viên mới điều chỉnh thủ công được sổ cái")
    void chiAdminDieuChinhDuoc() {
        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("delta", 100, "reason", "Tu cong them buoi"))
                .when().post("/registrations/" + registrationId + "/credit-ledger/adjust")
                .then().statusCode(403);

        assertThat(soDu()).isEqualTo(12);
    }

    // ==================================================== tiện ích

    private int soDu() {
        return given().header(auth(tokenHoiVien))
                .when().get("/registrations/" + registrationId + "/credit-ledger")
                .then().statusCode(200).extract().path("soDuHienTai");
    }

    private String trangThai(int sessionId) {
        return given().header(auth(tokenHoiVien))
                .when().get("/pt-sessions/" + sessionId)
                .then().statusCode(200).extract().path("status");
    }

    private Map<String, Object> bodyDatLich() {
        var f = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        var batDau = OffsetDateTime.now().plusDays(2).withNano(0);

        Map<String, Object> body = new HashMap<>();
        body.put("registrationId", registrationId);
        body.put("trainerId", 1);
        body.put("scheduledStart", batDau.format(f));
        body.put("scheduledEnd", batDau.plusHours(1).format(f));
        body.put("roomName", "Khu ta tu do");
        return body;
    }

    private int datLich() {
        return given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(bodyDatLich())
                .when().post("/pt-sessions")
                .then().statusCode(201)
                .body("status", equalTo("PENDING_TRAINER"))
                .extract().path("id");
    }

    private int datLichVaDuyet() {
        int id = datLich();
        given().header(auth(tokenPT))
                .when().post("/pt-sessions/" + id + "/approve")
                .then().statusCode(200).body("status", equalTo("SCHEDULED"));
        return id;
    }

    private int muaVaKichHoatGoiPT() {
        Long idGoi = jdbc.queryForObject(
                "SELECT id FROM memberships WHERE code = 'PT-12'", Long.class);

        int id = given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("membershipId", idGoi))
                .when().post("/registrations")
                .then().statusCode(201).extract().path("id");

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
