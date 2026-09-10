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

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Kiểm thử module thanh toán và check-in qua API thật.
 *
 * <p>Trọng tâm là hai điểm kiểm soát thất thoát:
 * <ul>
 *   <li>Tiền mặt bắt buộc gắn ca làm việc, cuối ca phải đối soát</li>
 *   <li>Check-in ghi lại CẢ lượt bị từ chối để đo hiệu quả chống gian lận</li>
 * </ul>
 * Cùng với điểm nối quan trọng nhất: thu đủ tiền thì hợp đồng tự kích hoạt và
 * sổ cái tự được cấp buổi.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThanhToanVaCheckInTest {

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

    private String tokenHoiVien, tokenLeTan, tokenKeToan;
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

        dangKy("Le Thi Hoa", "0966777888");
        doiVaiTro("0966777888", "ACCOUNTANT");
        taoNhanVien("0966777888", "EM-021", "ACCOUNTING");
        tokenKeToan = dangNhap("0966777888");

        registrationId = muaGoi("PT-12");
        memberId = jdbc.queryForObject("SELECT id FROM members LIMIT 1", Integer.class);
    }

    // ==================================================== điểm nối quan trọng

    @Test
    @DisplayName("Thu đủ tiền thì hợp đồng TỰ kích hoạt và sổ cái TỰ được cấp buổi")
    void thuDuTienThiKichHoatVaCapBuoi() {
        moCa(500_000);
        int invoiceId = xuatHoaDon();

        // Trước khi trả tiền: hợp đồng chờ thanh toán, sổ cái trống
        assertThat(trangThaiHopDong()).isEqualTo("PENDING_PAYMENT");
        assertThat(soDuSoCai()).isZero();

        thuTien(invoiceId, 4_200_000, "CASH");

        // Sau khi trả đủ: cả ba thứ cùng thay đổi trong một giao dịch
        assertThat(trangThaiHopDong()).isEqualTo("ACTIVE");
        assertThat(soDuSoCai()).isEqualTo(12);

        given().header(auth(tokenLeTan)).when().get("/billing/invoices/" + invoiceId)
                .then().statusCode(200)
                .body("status", equalTo("PAID"))
                .body("balanceDue", comparesEqualTo(0f));
    }

    @Test
    @DisplayName("Trả một phần thì chưa kích hoạt hợp đồng")
    void traMotPhanChuaKichHoat() {
        moCa(0);
        int invoiceId = xuatHoaDon();

        thuTien(invoiceId, 2_000_000, "CASH");

        given().header(auth(tokenLeTan)).when().get("/billing/invoices/" + invoiceId)
                .then().body("status", equalTo("PARTIALLY_PAID"))
                .body("balanceDue", comparesEqualTo(2200000f));

        assertThat(trangThaiHopDong()).isEqualTo("PENDING_PAYMENT");
        assertThat(soDuSoCai()).isZero();

        // Trả nốt phần còn lại thì mới kích hoạt
        thuTien(invoiceId, 2_200_000, "CASH");
        assertThat(trangThaiHopDong()).isEqualTo("ACTIVE");
        assertThat(soDuSoCai()).isEqualTo(12);
    }

    @Test
    @DisplayName("Không thu quá số tiền còn nợ")
    void khongThuQuaSoConNo() {
        moCa(0);
        int invoiceId = xuatHoaDon();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("invoiceId", invoiceId, "amount", 99_000_000, "method", "CASH"))
                .when().post("/billing/payments")
                .then().statusCode(400)
                .body("code", equalTo("OVERPAY"));
    }

    @Test
    @DisplayName("Hợp đồng vừa chốt mua hiện NGAY trong Công nợ, không cần chờ lễ tân xác nhận")
    void hopDongMoiHienNgayTrongCongNo() {
        given().header(auth(tokenLeTan)).when().get("/billing/invoices/unpaid")
                .then().statusCode(200)
                .body("registrationId", hasItem(registrationId))
                .body("find { it.registrationId == " + registrationId + " }.status", equalTo("UNPAID"));
    }

    @Test
    @DisplayName("Mỗi hợp đồng chỉ xuất được một hóa đơn")
    void moiHopDongMotHoaDon() {
        xuatHoaDon();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("registrationId", registrationId))
                .when().post("/billing/invoices")
                .then().statusCode(409)
                .body("code", equalTo("INVOICE_EXISTS"));
    }

    // ============================================ xác nhận gói tập (1 lần bấm)

    @Test
    @DisplayName("Chưa có hóa đơn: xác nhận gói tập tự tạo hóa đơn, thu đủ tiền và kích hoạt")
    void xacNhanGoiTap_chuaCoHoaDon() {
        xacNhanGoiTap("VIETQR").then().statusCode(200).body("status", equalTo("ACTIVE"));

        assertThat(trangThaiHopDong()).isEqualTo("ACTIVE");
        assertThat(soDuSoCai()).isEqualTo(12);
    }

    @Test
    @DisplayName("Đã có hóa đơn CHƯA thu (xuất riêng qua kế toán): xác nhận gói tập vẫn thu " +
                "được tiền vào đúng hóa đơn đó, không báo lỗi INVOICE_EXISTS")
    void xacNhanGoiTap_daCoHoaDonChuaThu() {
        int invoiceId = xuatHoaDon();

        xacNhanGoiTap("VIETQR").then().statusCode(200).body("status", equalTo("ACTIVE"));

        assertThat(trangThaiHopDong()).isEqualTo("ACTIVE");
        given().header(auth(tokenLeTan)).when().get("/billing/invoices/" + invoiceId)
                .then().body("status", equalTo("PAID"));
    }

    @Test
    @DisplayName("Hóa đơn đã trả một phần: xác nhận gói tập chỉ thu đúng phần còn nợ")
    void xacNhanGoiTap_daTraMotPhan() {
        moCa(0);
        int invoiceId = xuatHoaDon();
        thuTien(invoiceId, 2_000_000, "CASH");

        xacNhanGoiTap("VIETQR").then().statusCode(200).body("status", equalTo("ACTIVE"));

        given().header(auth(tokenLeTan)).when().get("/billing/invoices/" + invoiceId)
                .then().body("status", equalTo("PAID"))
                .body("paidAmount", comparesEqualTo(4200000f));
    }

    @Test
    @DisplayName("Hóa đơn đã thu đủ từ trước: xác nhận gói tập báo ĐÃ THANH TOÁN, " +
                "không phải lỗi INVOICE_EXISTS")
    void xacNhanGoiTap_daThuDu() {
        moCa(0);
        thuTien(xuatHoaDon(), 4_200_000, "CASH");

        xacNhanGoiTap("VIETQR").then().statusCode(409).body("code", equalTo("ALREADY_PAID"));
    }

    // ==================================================== ca làm việc

    @Test
    @DisplayName("Chưa mở ca thì KHÔNG thu được tiền mặt")
    void chuaMoCaKhongThuTienMat() {
        int invoiceId = xuatHoaDon();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("invoiceId", invoiceId, "amount", 4_200_000, "method", "CASH"))
                .when().post("/billing/payments")
                .then().statusCode(400)
                .body("code", equalTo("NO_OPEN_SHIFT"));
    }

    @Test
    @DisplayName("Chuyển khoản KHÔNG cần mở ca vì không nằm trong két")
    void chuyenKhoanKhongCanMoCa() {
        int invoiceId = xuatHoaDon();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("invoiceId", invoiceId, "amount", 4_200_000, "method", "VIETQR"))
                .when().post("/billing/payments")
                .then().statusCode(201)
                .body("cashShiftId", nullValue());

        assertThat(trangThaiHopDong()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Không mở được hai ca cùng lúc")
    void khongMoHaiCa() {
        moCa(500_000);

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("openingBalance", 300_000))
                .when().post("/billing/cash-shifts/open")
                .then().statusCode(409)
                .body("code", equalTo("SHIFT_ALREADY_OPEN"));
    }

    @Test
    @DisplayName("Đóng ca khớp sổ thì ca đóng bình thường")
    void dongCaKhopSo() {
        moCa(500_000);
        thuTien(xuatHoaDon(), 4_200_000, "CASH");

        // Đầu ca 500.000 + thu 4.200.000 = 4.700.000
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("countedCash", 4_700_000))
                .when().post("/billing/cash-shifts/close")
                .then().statusCode(200)
                .body("status", equalTo("CLOSED"))
                .body("expectedCash", comparesEqualTo(4700000f))
                .body("difference", comparesEqualTo(0f));
    }

    @Test
    @DisplayName("Tiền đếm được LỆCH sổ thì bắt buộc giải trình")
    void lechTienPhaiGiaiTrinh() {
        moCa(500_000);
        thuTien(xuatHoaDon(), 4_200_000, "CASH");

        // Thiếu 50.000 mà không ghi lý do
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("countedCash", 4_650_000))
                .when().post("/billing/cash-shifts/close")
                .then().statusCode(400)
                .body("code", equalTo("REASON_REQUIRED"));

        // Có lý do thì đóng được, nhưng đánh dấu là ca lệch tiền
        Map<String, Object> body = new HashMap<>();
        body.put("countedCash", 4_650_000);
        body.put("reason", "Tra thua tien khach luc 15h, da ghi so tay");

        given().contentType(ContentType.JSON).header(auth(tokenLeTan)).body(body)
                .when().post("/billing/cash-shifts/close")
                .then().statusCode(200)
                .body("status", equalTo("DISCREPANCY"))
                .body("difference", comparesEqualTo(-50000f));
    }

    // ==================================================== hoàn tiền

    @Test
    @DisplayName("Hoàn tiền ghi bút toán ÂM, cộng dồn ra số thực giữ lại")
    void hoanTienGhiButToanAm() {
        moCa(0);
        int invoiceId = xuatHoaDon();
        int paymentId = thuTien(invoiceId, 4_200_000, "CASH");

        Map<String, Object> body = new HashMap<>();
        body.put("amount", 1_000_000);
        body.put("reason", "Hoi vien huy goi sau 1 tuan");

        given().contentType(ContentType.JSON).header(auth(tokenKeToan)).body(body)
                .when().post("/billing/payments/" + paymentId + "/refund")
                .then().statusCode(200)
                .body("paymentType", equalTo("REFUND"))
                .body("amount", comparesEqualTo(-1000000f));

        // Cộng dồn: 4.200.000 − 1.000.000 = 3.200.000 thực giữ lại
        given().header(auth(tokenKeToan)).when().get("/billing/invoices/" + invoiceId)
                .then().body("paidAmount", comparesEqualTo(3200000f));
    }

    @Test
    @DisplayName("Hoàn tiền bắt buộc ghi lý do")
    void hoanTienPhaiCoLyDo() {
        moCa(0);
        int paymentId = thuTien(xuatHoaDon(), 4_200_000, "CASH");

        given().contentType(ContentType.JSON).header(auth(tokenKeToan))
                .body(Map.of("amount", 500_000, "reason", ""))
                .when().post("/billing/payments/" + paymentId + "/refund")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("Lễ tân KHÔNG tự hoàn tiền được, phải qua kế toán")
    void leTanKhongHoanTien() {
        moCa(0);
        int paymentId = thuTien(xuatHoaDon(), 4_200_000, "CASH");

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("amount", 500_000, "reason", "Tu hoan"))
                .when().post("/billing/payments/" + paymentId + "/refund")
                .then().statusCode(403);
    }

    // ==================================================== check-in

    @Test
    @DisplayName("Hợp đồng đang chạy thì cho vào, trả kèm số ngày còn lại")
    void hopDongDangChayThiChoVao() {
        kichHoatHopDong();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId))
                .when().post("/check-ins")
                .then().statusCode(200)
                .body("result", equalTo("ALLOWED"))
                .body("choPhepVao", equalTo(true))
                .body("memberName", equalTo("Nguyen Van An"))
                .body("soNgayConLai", greaterThan(0))
                .body("thongBao", containsString("Mời vào tập"));
    }

    @Test
    @DisplayName("Chưa thanh toán thì bị chặn, nhưng lễ tân được phép cho vào có ghi nhận")
    void chuaThanhToanThiChan() {
        // Kích hoạt hợp đồng nhưng để hóa đơn chưa trả
        int invoiceId = xuatHoaDon();
        given().header(auth(tokenLeTan))
                .when().post("/registrations/" + registrationId + "/activate")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId))
                .when().post("/check-ins")
                .then().statusCode(200)
                .body("result", equalTo("DENIED_UNPAID"))
                .body("choPhepVao", equalTo(false));

        // Lễ tân bỏ qua cảnh báo — được ghi lại rõ để truy trách nhiệm
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId, "override", true))
                .when().post("/check-ins")
                .then().statusCode(200)
                .body("result", equalTo("ALLOWED_OVERRIDE"))
                .body("choPhepVao", equalTo(true));

        assertThat(invoiceId).isPositive();
    }

    @Test
    @DisplayName("Chưa có hợp đồng nào chạy thì bị chặn vì chưa thanh toán")
    void chuaCoHopDongThiChan() {
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId))
                .when().post("/check-ins")
                .then().statusCode(200)
                .body("result", equalTo("DENIED_UNPAID"))
                .body("thongBao", containsString("thu tiền"));
    }

    @Test
    @DisplayName("Đang ở TRONG phòng mà lại quét vào thì bị đánh dấu nghi dùng chung tài khoản")
    void nghiDungChungTaiKhoan() {
        kichHoatHopDong();
        quetVao();

        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId))
                .when().post("/check-ins")
                .then().statusCode(200)
                .body("incidentType", equalTo("SUSPECTED_SHARING"));
    }

    @Test
    @DisplayName("Quét ra rồi thì lượt vào được đóng lại")
    void quetRaThiDongLuot() {
        kichHoatHopDong();
        quetVao();

        given().header(auth(tokenLeTan))
                .when().post("/check-ins/" + memberId + "/check-out")
                .then().statusCode(200);

        given().header(auth(tokenLeTan)).when().get("/check-ins/inside")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @DisplayName("Lượt bị từ chối VẪN được ghi lại để thống kê chống thất thoát")
    void luotBiTuChoiVanGhiLai() {
        // 3 lượt bị chặn vì chưa có hợp đồng
        for (int i = 0; i < 3; i++) quetVaoBoQuaKetQua();

        given().header(auth(tokenKeToan)).when().get("/check-ins/stats?days=1")
                .then().statusCode(200)
                .body("DENIED_UNPAID", equalTo(3));
    }

    @Test
    @DisplayName("Hội viên thường KHÔNG tự quét check-in cho mình được")
    void hoiVienKhongTuQuet() {
        given().contentType(ContentType.JSON).header(auth(tokenHoiVien))
                .body(Map.of("memberId", memberId))
                .when().post("/check-ins")
                .then().statusCode(403);
    }

    // ==================================================== tiện ích

    private void moCa(int tienDauCa) {
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("openingBalance", tienDauCa))
                .when().post("/billing/cash-shifts/open").then().statusCode(201);
    }

    /**
     * Hóa đơn giờ được xuất NGAY lúc lập hợp đồng (xem RegistrationService),
     * không còn chờ tới lúc gọi endpoint này — nên chỉ cần lấy lại id đã có sẵn.
     */
    private int xuatHoaDon() {
        return jdbc.queryForObject(
                "SELECT id FROM invoices WHERE registration_id = ?", Integer.class, registrationId);
    }

    private int thuTien(int invoiceId, int soTien, String hinhThuc) {
        return given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("invoiceId", invoiceId, "amount", soTien, "method", hinhThuc))
                .when().post("/billing/payments").then().statusCode(201)
                .extract().path("id");
    }

    private io.restassured.response.Response xacNhanGoiTap(String hinhThuc) {
        return given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("method", hinhThuc))
                .when().post("/billing/registrations/" + registrationId + "/confirm");
    }

    private void kichHoatHopDong() {
        moCa(0);
        thuTien(xuatHoaDon(), 4_200_000, "CASH");
    }

    private void quetVao() {
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId))
                .when().post("/check-ins").then().statusCode(200);
    }

    private void quetVaoBoQuaKetQua() {
        given().contentType(ContentType.JSON).header(auth(tokenLeTan))
                .body(Map.of("memberId", memberId)).when().post("/check-ins");
    }

    private String trangThaiHopDong() {
        return given().header(auth(tokenHoiVien))
                .when().get("/registrations/" + registrationId)
                .then().statusCode(200).extract().path("status");
    }

    private int soDuSoCai() {
        return given().header(auth(tokenHoiVien))
                .when().get("/registrations/" + registrationId + "/credit-ledger")
                .then().statusCode(200).extract().path("soDuHienTai");
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
