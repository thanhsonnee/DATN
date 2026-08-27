package com.gym.membership;

import com.gym.membership.repository.RegistrationRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Kiểm thử tích hợp module gói tập và hợp đồng trên PostgreSQL thật.
 *
 * <p>Trọng tâm: luồng chốt mua sinh ra hồ sơ hội viên, snapshot điều khoản,
 * phân quyền theo vai trò, và quy tắc bảo lưu một lần.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationApiTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gymdb_test")
            .withUsername("gym")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.jwt.secret", () -> "chuoi-bi-mat-danh-rieng-cho-kiem-thu-du-32-ky-tu");
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RegistrationRepository registrationRepo;

    private String tokenHoiVien;
    private String tokenLeTan;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";

        jdbc.execute("TRUNCATE persons, users, members, registrations RESTART IDENTITY CASCADE");
        jdbc.execute("ALTER SEQUENCE member_code_seq RESTART 1");
        jdbc.execute("ALTER SEQUENCE registration_code_seq RESTART 1");

        tokenHoiVien = dangKy("Nguyen Van An", "0912345678");

        dangKy("Vu Thi Mai", "0987654321");
        jdbc.update("UPDATE users SET primary_role = 'RECEPTIONIST' WHERE username = ?", "0987654321");
        tokenLeTan = dangNhap("0987654321");
    }

    // ---------------------------------------------------------------- bảng giá

    @Test
    @DisplayName("Bảng giá xem được mà không cần đăng nhập")
    void bangGia_congKhai() {
        given().when().get("/memberships")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(8))
                .body("code", hasItems("FIT-06M", "PT-12", "COMBO-12M-PT24"));
    }

    // -------------------------------------------------------------- chốt mua

    @Test
    @DisplayName("Chốt mua gói đầu tiên thì tạo luôn hồ sơ hội viên")
    void chotMua_taoHoSoHoiVien() {
        // Trước khi mua: có tài khoản nhưng CHƯA phải hội viên (phương án A)
        given().header(auth(tokenHoiVien)).when().get("/auth/me")
                .then().body("isMember", equalTo(false))
                .body("memberCode", nullValue());

        muaGoi("FIT-06M", null, null)
                .then().statusCode(201)
                .body("memberCode", startsWith("MB-"))
                .body("registrationCode", startsWith("REG-"))
                .body("status", equalTo("PENDING_PAYMENT"));

        // Sau khi mua: đã là hội viên thật
        given().header(auth(tokenHoiVien)).when().get("/auth/me")
                .then().body("isMember", equalTo(true))
                .body("memberCode", startsWith("MB-"));
    }

    @Test
    @DisplayName("Mua gói lần hai dùng lại hồ sơ hội viên cũ, không tạo mã mới")
    void chotMua_lanHaiDungLaiHoSo() {
        String maLan1 = muaGoi("FIT-06M", null, null).then().extract().path("memberCode");
        String maLan2 = muaGoi("PT-12", null, null).then().extract().path("memberCode");

        assertThat(maLan2).isEqualTo(maLan1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Điều khoản được sao chép sang hợp đồng, đổi giá gói không ảnh hưởng hợp đồng cũ")
    void chotMua_snapshotDieuKhoan() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");

        // Phòng gym tăng giá gói sau khi hợp đồng đã ký
        jdbc.update("UPDATE memberships SET price = 9999999 WHERE code = 'FIT-06M'");

        given().header(auth(tokenHoiVien)).when().get("/registrations/" + id)
                .then().statusCode(200)
                // Hợp đồng giữ nguyên giá lúc ký, không đọc ngược từ bảng gói tập
                .body("listPrice", comparesEqualTo(3000000f))
                .body("finalPrice", comparesEqualTo(3000000f));
    }

    @Test
    @DisplayName("Có giảm giá thì bắt buộc ghi tên chương trình khuyến mãi")
    void chotMua_giamGiaPhaiCoLyDo() {
        muaGoi("FIT-06M", 300000, null)
                .then().statusCode(400)
                .body("code", equalTo("DISCOUNT_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("Giảm giá vượt quá giá gói thì bị từ chối")
    void chotMua_giamGiaVuotGiaGoi() {
        muaGoi("FIT-06M", 99_000_000, "Khuyen mai ao")
                .then().statusCode(400)
                .body("code", equalTo("DISCOUNT_TOO_LARGE"));
    }

    @Test
    @DisplayName("Thành tiền luôn bằng giá niêm yết trừ tiền giảm")
    void chotMua_thanhTienNhatQuan() {
        muaGoi("PT-12", 420_000, "Khuyen mai he 2026")
                .then().statusCode(201)
                .body("listPrice", comparesEqualTo(4200000f))
                .body("discountAmount", comparesEqualTo(420000f))
                .body("finalPrice", comparesEqualTo(3780000f));
    }

    @Test
    @DisplayName("Hội viên tự mua thì luôn mua cho chính mình, không mua hộ người khác được")
    void chotMua_khongMuaHoNguoiKhac() {
        // Cố tình gửi kèm personId của lễ tân
        Long personIdLeTan = jdbc.queryForObject(
                "SELECT person_id FROM users WHERE username = '0987654321'", Long.class);

        Map<String, Object> body = new HashMap<>();
        body.put("membershipId", idGoi("FIT-06M"));
        body.put("personId", personIdLeTan);

        String maHoiVien = given().contentType(ContentType.JSON).header(auth(tokenHoiVien)).body(body)
                .when().post("/registrations")
                .then().statusCode(201)
                .extract().path("memberCode");

        // Hồ sơ hội viên phải thuộc về AN, không phải lễ tân
        String tenChuHopDong = jdbc.queryForObject(
                "SELECT p.full_name FROM members m JOIN persons p ON p.id = m.person_id "
                        + "WHERE m.member_code = ?", String.class, maHoiVien);
        assertThat(tenChuHopDong).isEqualTo("Nguyen Van An");
    }

    // ------------------------------------------------------------ kích hoạt

    @Test
    @DisplayName("Hội viên không tự kích hoạt hợp đồng của mình được")
    void kichHoat_hoiVienBiChan() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");

        given().header(auth(tokenHoiVien))
                .when().post("/registrations/" + id + "/activate")
                .then().statusCode(403)
                .body("code", equalTo("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Lễ tân kích hoạt thì hợp đồng chạy và ngày hết hạn tính đúng theo thời hạn gói")
    void kichHoat_tinhDungNgayHetHan() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");

        String endDate = given().header(auth(tokenLeTan))
                .when().post("/registrations/" + id + "/activate")
                .then().statusCode(200)
                .body("status", equalTo("ACTIVE"))
                .body("startDate", equalTo(LocalDate.now().toString()))
                .extract().path("endDate");

        // Gói 180 ngày: ngày bắt đầu tính là ngày đầu tiên nên cộng 179
        assertThat(LocalDate.parse(endDate)).isEqualTo(LocalDate.now().plusDays(179));
    }

    @Test
    @DisplayName("Không kích hoạt lại được hợp đồng đã chạy")
    void kichHoat_khongLapLai() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");
        kichHoat(id);

        given().header(auth(tokenLeTan))
                .when().post("/registrations/" + id + "/activate")
                .then().statusCode(400)
                .body("code", equalTo("INVALID_STATE"));
    }

    // -------------------------------------------------------------- bảo lưu

    @Test
    @DisplayName("Duyệt bảo lưu thì ngày hết hạn được đẩy lùi đúng số ngày bảo lưu")
    void baoLuu_dayLuiNgayHetHan() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");
        String hanCu = kichHoat(id);

        LocalDate tu = LocalDate.now().plusDays(10);
        LocalDate den = tu.plusDays(19);          // 20 ngày kể cả ngày đầu

        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("fromDate", tu.toString(),
                             "toDate", den.toString(),
                             "reason", "Di cong tac nuoc ngoai", "reasonType", "TRAVEL"))
        .when().post("/registrations/" + id + "/freeze")
        .then().statusCode(200)
                .body("freeze.status", equalTo("PENDING"))
                // Số ngày do CSDL tự tính, không do ứng dụng gửi lên
                .body("freeze.days", equalTo(20));

        String hanMoi = given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("approved", true))
        .when().post("/registrations/" + id + "/freeze/decision")
        .then().statusCode(200)
                .body("freeze.status", equalTo("APPROVED"))
                .extract().path("endDate");

        assertThat(LocalDate.parse(hanMoi))
                .as("Hội viên không mất số ngày đã trả tiền")
                .isEqualTo(LocalDate.parse(hanCu).plusDays(20));
    }

    @Test
    @DisplayName("Mỗi hợp đồng chỉ được bảo lưu MỘT lần")
    void baoLuu_chiMotLan() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");
        kichHoat(id);
        xinBaoLuu(id, LocalDate.now().plusDays(10), 10).then().statusCode(200);

        xinBaoLuu(id, LocalDate.now().plusDays(60), 10)
                .then().statusCode(409)
                .body("code", equalTo("ALREADY_FROZEN_ONCE"));
    }

    @Test
    @DisplayName("Gói không cho bảo lưu thì từ chối ngay")
    void baoLuu_goiKhongChoPhep() {
        int id = muaGoi("PT-12", null, null).then().extract().path("id");   // PT-12 có maxFreezeDays = 0
        kichHoat(id);

        xinBaoLuu(id, LocalDate.now().plusDays(10), 5)
                .then().statusCode(400)
                .body("code", equalTo("FREEZE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Xin bảo lưu dài hơn hạn mức của gói thì bị từ chối")
    void baoLuu_vuotHanMuc() {
        int id = muaGoi("FIT-03M", null, null).then().extract().path("id"); // tối đa 14 ngày
        kichHoat(id);

        xinBaoLuu(id, LocalDate.now().plusDays(10), 30)
                .then().statusCode(400)
                .body("code", equalTo("FREEZE_TOO_LONG"));
    }

    @Test
    @DisplayName("Báo bảo lưu quá gấp thì bị từ chối")
    void baoLuu_baoQuaGap() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");
        kichHoat(id);

        xinBaoLuu(id, LocalDate.now().plusDays(1), 10)
                .then().statusCode(400)
                .body("code", equalTo("FREEZE_TOO_LATE"));
    }

    @Test
    @DisplayName("Hợp đồng chưa kích hoạt thì chưa được xin bảo lưu")
    void baoLuu_hopDongChuaChay() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");

        xinBaoLuu(id, LocalDate.now().plusDays(10), 10)
                .then().statusCode(400)
                .body("code", equalTo("INVALID_STATE"));
    }

    // ------------------------------------------------------------- truy vấn

    @Test
    @DisplayName("Danh sách chờ thanh toán hiện đúng hợp đồng vừa chốt mua, mất đi khi đã thu tiền")
    void choThanhToan_hienDungHopDong() {
        int id = muaGoi("FIT-06M", null, null).then().extract().path("id");

        given().header(auth(tokenLeTan)).when().get("/registrations/pending-payment")
                .then().statusCode(200)
                .body("id", hasItem(id))
                .body("find { it.id == " + id + " }.status", equalTo("PENDING_PAYMENT"));

        kichHoat(id);

        given().header(auth(tokenLeTan)).when().get("/registrations/pending-payment")
                .then().statusCode(200)
                .body("id", not(hasItem(id)));
    }

    @Test
    @DisplayName("Hội viên KHÔNG xem được danh sách chờ thanh toán của người khác")
    void choThanhToan_hoiVienBiChan() {
        given().header(auth(tokenHoiVien)).when().get("/registrations/pending-payment")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("Người chưa mua gói xem hợp đồng của mình thì nhận danh sách rỗng, không phải lỗi")
    void hopDongCuaToi_chuaMuaGoi() {
        given().header(auth(tokenHoiVien)).when().get("/registrations/me")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @DisplayName("Hợp đồng sắp hết hạn chỉ nhân viên mới xem được")
    void sapHetHan_phanQuyen() {
        given().header(auth(tokenHoiVien)).when().get("/registrations/expiring")
                .then().statusCode(403);

        given().header(auth(tokenLeTan)).when().get("/registrations/expiring?days=14")
                .then().statusCode(200);
    }

    // ---------------------------------------------------------------- tiện ích

    private io.restassured.specification.RequestSpecification given_() {
        return given().contentType(ContentType.JSON);
    }

    private String dangKy(String hoTen, String soDienThoai) {
        return given_().body(Map.of("fullName", hoTen, "phone", soDienThoai, "password", "MatKhau@2026"))
                .when().post("/auth/register").then().statusCode(201)
                .extract().path("accessToken");
    }

    private String dangNhap(String soDienThoai) {
        return given_().body(Map.of("username", soDienThoai, "password", "MatKhau@2026"))
                .when().post("/auth/login").then().statusCode(200)
                .extract().path("accessToken");
    }

    private io.restassured.response.Response muaGoi(String maGoi, Integer giamGia, String lyDoGiam) {
        Map<String, Object> body = new HashMap<>();
        body.put("membershipId", idGoi(maGoi));
        if (giamGia != null) body.put("discountAmount", giamGia);
        if (lyDoGiam != null) body.put("discountReason", lyDoGiam);

        return given_().header(auth(tokenHoiVien)).body(body).when().post("/registrations");
    }

    private String kichHoat(int registrationId) {
        return given().header(auth(tokenLeTan))
                .when().post("/registrations/" + registrationId + "/activate")
                .then().statusCode(200).extract().path("endDate");
    }

    private io.restassured.response.Response xinBaoLuu(int id, LocalDate tu, int soNgay) {
        return given_().header(auth(tokenHoiVien))
                .body(Map.of("fromDate", tu.toString(),
                             "toDate", tu.plusDays(soNgay - 1L).toString(),
                             "reason", "Ly do bao luu", "reasonType", "PERSONAL"))
                .when().post("/registrations/" + id + "/freeze");
    }

    private Long idGoi(String maGoi) {
        return jdbc.queryForObject("SELECT id FROM memberships WHERE code = ?", Long.class, maGoi);
    }

    private io.restassured.http.Header auth(String token) {
        return new io.restassured.http.Header("Authorization", "Bearer " + token);
    }
}
