package com.gym.identity;

import com.gym.identity.domain.UserRole;
import com.gym.identity.repository.UserRepository;
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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Kiểm thử tích hợp module danh tính trên PostgreSQL THẬT (qua Testcontainers).
 *
 * <p>Dùng CSDL thật thay vì CSDL trong bộ nhớ vì lược đồ dựa nhiều vào các tính
 * năng riêng của PostgreSQL: ràng buộc CHECK, chỉ mục duy nhất có điều kiện,
 * trigger cập nhật {@code updated_at}, và cột tự tính. CSDL giả lập sẽ bỏ qua
 * hết những thứ này và cho kết quả kiểm thử sai lệch.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTest {

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
    UserRepository userRepo;

    @Autowired
    JdbcTemplate jdbc;

    private static final String PHONE = "0912345678";
    private static final String PASSWORD = "MatKhau@2026";

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1/auth";

        // Phải dọn CẢ persons, không chỉ users: số điện thoại là duy nhất ở bảng
        // persons, nên còn sót một dòng là mọi ca đăng ký sau đều bị từ chối.
        jdbc.execute("TRUNCATE persons, users, members RESTART IDENTITY CASCADE");
    }

    // ------------------------------------------------------------------ đăng ký

    @Test
    @DisplayName("Đăng ký thành công thì trả về token và tài khoản vai trò MEMBER")
    void register_thanhCong() {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", PHONE, "password", PASSWORD))
        .when().post("/register")
        .then().statusCode(201)
                .body("accessToken", not(emptyOrNullString()))
                .body("user.role", equalTo("MEMBER"))
                // Chưa mua gói nên CHƯA có hồ sơ hội viên (phương án A)
                .body("user.isMember", equalTo(false));
    }

    @Test
    @DisplayName("Phản hồi đăng ký TUYỆT ĐỐI không chứa mật khẩu hay mã băm mật khẩu")
    void register_khongLoMatKhau() {
        String body = given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", PHONE, "password", PASSWORD))
                .when().post("/register")
                .then().statusCode(201)
                .extract().asString();

        assertThat(body.toLowerCase())
                .as("Phản hồi API không được lộ bất kỳ dấu vết nào của mật khẩu")
                .doesNotContain("password")
                .doesNotContain(PASSWORD.toLowerCase())
                .doesNotContain("$2a$");   // tiền tố của chuỗi băm BCrypt
    }

    @Test
    @DisplayName("Client tự khai vai trò ADMIN vẫn chỉ được cấp vai trò MEMBER")
    void register_khongTheLeoThangDacQuyen() {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Ke tan cong", "phone", PHONE, "password", PASSWORD,
                             "role", "ADMIN", "primaryRole", "ADMIN"))
        .when().post("/register")
        .then().statusCode(201)
                .body("user.role", equalTo("MEMBER"));

        assertThat(userRepo.findByUsernameAndDeletedAtIsNull(PHONE))
                .get()
                .extracting(u -> u.getPrimaryRole())
                .isEqualTo(UserRole.MEMBER);
    }

    @Test
    @DisplayName("Trùng số điện thoại thì bị từ chối")
    void register_trungSoDienThoai() {
        dangKy();
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguoi khac", "phone", PHONE, "password", PASSWORD))
        .when().post("/register")
        .then().statusCode(409)
                .body("code", equalTo("PHONE_TAKEN"));
    }

    @Test
    @DisplayName("Số điện thoại sai định dạng Việt Nam thì bị từ chối")
    void register_soDienThoaiSaiDinhDang() {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", "123", "password", PASSWORD))
        .when().post("/register")
        .then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("fields.phone", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("Mật khẩu dưới 8 ký tự thì bị từ chối")
    void register_matKhauQuaNgan() {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", PHONE, "password", "123"))
        .when().post("/register")
        .then().statusCode(400)
                .body("fields.password", not(emptyOrNullString()));
    }

    // --------------------------------------------------------------- đăng nhập

    @Test
    @DisplayName("Đăng nhập đúng mật khẩu thì nhận được token")
    void login_thanhCong() {
        dangKy();
        given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", PASSWORD))
        .when().post("/login")
        .then().statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("tokenType", equalTo("Bearer"));
    }

    @Test
    @DisplayName("Tài khoản không tồn tại và sai mật khẩu trả về CÙNG một thông báo")
    void login_khongTietLoTaiKhoanCoTonTai() {
        dangKy();

        String saiMatKhau = given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", "SaiMatKhau@2026"))
                .when().post("/login").then().statusCode(401)
                .extract().path("message");

        String khongCoTaiKhoan = given().contentType(ContentType.JSON)
                .body(Map.of("username", "0900000000", "password", PASSWORD))
                .when().post("/login").then().statusCode(401)
                .extract().path("message");

        assertThat(saiMatKhau)
                .as("Hai thông báo phải giống hệt nhau, nếu không kẻ tấn công dò được tài khoản nào có thật")
                .isEqualTo(khongCoTaiKhoan);
    }

    @Test
    @DisplayName("Sai mật khẩu 5 lần thì khóa tạm, dù sau đó nhập đúng cũng không vào được")
    void login_khoaTamSauNhieuLanSai() {
        dangKy();

        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("username", PHONE, "password", "SaiRoi@2026"))
            .when().post("/login")
            .then().statusCode(401);
        }

        // Số lần sai PHẢI được lưu lại. Nếu ghi chung giao dịch với lệnh ném ngoại lệ
        // thì giao dịch quay lui và số đếm mất sạch — kẻ tấn công thử được vô hạn lần.
        var user = userRepo.findByUsernameAndDeletedAtIsNull(PHONE).orElseThrow();
        assertThat(user.getFailedAttempts()).isEqualTo((short) 5);
        assertThat(user.getAutoLockedUntil()).isNotNull();

        // Khóa TỰ ĐỘNG không được đụng tới lệnh khóa thủ công của Admin
        assertThat(user.getLockedUntil()).isNull();

        given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", PASSWORD))
        .when().post("/login")
        .then().statusCode(403)
                .body("code", equalTo("TEMPORARILY_LOCKED"));
    }

    @Test
    @DisplayName("Đăng nhập lại đúng mật khẩu thì số lần sai được đặt về 0")
    void login_datLaiSoLanSai() {
        dangKy();

        given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", "SaiRoi@2026"))
        .when().post("/login").then().statusCode(401);

        given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", PASSWORD))
        .when().post("/login").then().statusCode(200);

        var user = userRepo.findByUsernameAndDeletedAtIsNull(PHONE).orElseThrow();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    // -------------------------------------------------------- làm mới token

    @Test
    @DisplayName("Refresh token hợp lệ thì cấp được access token mới")
    void refresh_thanhCong() {
        String refreshToken = dangKyVaLayRefreshToken();

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
        .when().post("/refresh")
        .then().statusCode(200)
                .body("accessToken", not(emptyOrNullString()))
                .body("refreshToken", not(emptyOrNullString()))
                .body("user.phone", equalTo(PHONE));
    }

    @Test
    @DisplayName("Access token KHÔNG dùng được để refresh — sai loại token thì bị chặn")
    void refresh_dungNhamAccessToken() {
        String accessToken = dangKyVaLayToken();

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", accessToken))
        .when().post("/refresh")
        .then().statusCode(401)
                .body("code", equalTo("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("Refresh token bị sửa chữ ký thì bị chặn")
    void refresh_tokenGiaMao() {
        String refreshToken = dangKyVaLayRefreshToken();

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken + "chuoi-them-vao"))
        .when().post("/refresh")
        .then().statusCode(401)
                .body("code", equalTo("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("Tài khoản bị khóa thủ công thì refresh token cũ cũng vô hiệu ngay")
    void refresh_taiKhoanBiKhoa() {
        String refreshToken = dangKyVaLayRefreshToken();

        jdbc.update("UPDATE users SET status = 'LOCKED', locked_reason = 'Thử nghiệm' WHERE username = ?", PHONE);

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
        .when().post("/refresh")
        .then().statusCode(403)
                .body("code", equalTo("ACCOUNT_LOCKED"));
    }

    // ------------------------------------------------------- phân quyền endpoint

    @Test
    @DisplayName("Gọi /me mà không có token thì bị chặn với mã 401")
    void me_khongCoToken() {
        when_get_me(null).then().statusCode(401);
    }

    @Test
    @DisplayName("Token bị sửa chữ ký thì bị chặn")
    void me_tokenGiaMao() {
        String token = dangKyVaLayToken();
        when_get_me(token + "chuoi-them-vao").then().statusCode(401);
    }

    @Test
    @DisplayName("Token hợp lệ thì đọc được thông tin tài khoản")
    void me_tokenHopLe() {
        String token = dangKyVaLayToken();
        when_get_me(token).then().statusCode(200)
                .body("phone", equalTo(PHONE))
                .body("role", equalTo("MEMBER"))
                .body("isMember", equalTo(false));
    }

    // ------------------------------------------------------------ đổi mật khẩu

    @Test
    @DisplayName("Đổi mật khẩu thành công thì mật khẩu cũ hết hiệu lực")
    void doiMatKhau_thanhCong() {
        String token = dangKyVaLayToken();
        String matKhauMoi = "MatKhauMoi@2026";

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token)
                .body(Map.of("currentPassword", PASSWORD, "newPassword", matKhauMoi))
        .when().post("/change-password")
        .then().statusCode(204);

        given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", PASSWORD))
        .when().post("/login").then().statusCode(401);

        given().contentType(ContentType.JSON)
                .body(Map.of("username", PHONE, "password", matKhauMoi))
        .when().post("/login").then().statusCode(200);
    }

    @Test
    @DisplayName("Nhập sai mật khẩu hiện tại thì không đổi được")
    void doiMatKhau_saiMatKhauHienTai() {
        String token = dangKyVaLayToken();

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token)
                .body(Map.of("currentPassword", "SaiRoi@2026", "newPassword", "MatKhauMoi@2026"))
        .when().post("/change-password")
        .then().statusCode(400)
                .body("code", equalTo("WRONG_PASSWORD"));
    }

    @Test
    @DisplayName("Đổi mật khẩu mà không đăng nhập thì bị chặn")
    void doiMatKhau_khongCoToken() {
        given().contentType(ContentType.JSON)
                .body(Map.of("currentPassword", PASSWORD, "newPassword", "MatKhauMoi@2026"))
        .when().post("/change-password")
        .then().statusCode(401);
    }

    // ----------------------------------------------------------------- tiện ích

    private void dangKy() {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", PHONE, "password", PASSWORD))
        .when().post("/register").then().statusCode(201);
    }

    private String dangKyVaLayRefreshToken() {
        return given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", PHONE, "password", PASSWORD))
                .when().post("/register").then().statusCode(201)
                .extract().path("refreshToken");
    }

    private String dangKyVaLayToken() {
        return given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Nguyen Van An", "phone", PHONE, "password", PASSWORD))
                .when().post("/register").then().statusCode(201)
                .extract().path("accessToken");
    }

    private io.restassured.response.Response when_get_me(String token) {
        var req = given();
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return req.when().get("/me");
    }
}
