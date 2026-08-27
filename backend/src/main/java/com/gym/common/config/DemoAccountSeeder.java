package com.gym.common.config;

import com.gym.identity.domain.*;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tạo sẵn tài khoản cho đủ 6 vai trò, để đăng nhập thử ngay mà không phải
 * sửa cơ sở dữ liệu bằng tay.
 *
 * <p>Chỉ chạy khi bảng tài khoản còn trống, nên không bao giờ ghi đè dữ liệu thật.
 *
 * <p><b>Vì sao phải tạo sẵn:</b> màn hình đăng ký công khai luôn gán cứng vai trò
 * hội viên — đó là chủ ý, để không ai tự nâng quyền cho mình. Tài khoản nhân viên
 * chỉ được tạo từ bên trong hệ thống, và trước khi có màn hình quản trị nhân sự
 * thì đây là đường duy nhất.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DemoAccountSeeder {

    /** Mật khẩu chung cho mọi tài khoản thử. ĐỔI trước khi triển khai thật. */
    private static final String MAT_KHAU = "MatKhau@2026";

    private final PersonRepository personRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner seedDemoAccounts() {
        return args -> {
            if (userRepo.count() > 0) return;

            taoNhanVien("0900000001", "Nguyễn Quản Trị", UserRole.ADMIN,
                    "EM-001", Department.TRAINING, "Chủ phòng gym", 20_000_000, null);

            taoNhanVien("0900000002", "Trần Bình", UserRole.TRAINER,
                    "EM-005", Department.TRAINING, "Huấn luyện viên cá nhân",
                    8_000_000, TrainerLevel.SENIOR);

            taoNhanVien("0900000003", "Phạm Dũng", UserRole.SALE,
                    "EM-012", Department.SALES, "Nhân viên kinh doanh", 7_000_000, null);

            taoNhanVien("0900000004", "Vũ Thị Mai", UserRole.RECEPTIONIST,
                    "EM-018", Department.FRONT_DESK, "Lễ tân", 6_500_000, null);

            taoNhanVien("0900000005", "Lê Thị Hoa", UserRole.ACCOUNTANT,
                    "EM-021", Department.ACCOUNTING, "Kế toán", 9_000_000, null);

            // Hội viên: chỉ có persons + users, CHƯA có hồ sơ hội viên.
            // Hồ sơ chỉ sinh ra khi mua gói đầu tiên (phương án A).
            taoTaiKhoan("0912345678", "Nguyễn Văn An", UserRole.MEMBER);

            log.info("""

                    ┌─────────────────────────────────────────────────────────┐
                    │  ĐÃ TẠO 6 TÀI KHOẢN THỬ — mật khẩu chung: MatKhau@2026  │
                    ├─────────────────────────────────────────────────────────┤
                    │  0900000001   Quản trị viên                             │
                    │  0900000002   Huấn luyện viên  (Trần Bình)              │
                    │  0900000003   Nhân viên kinh doanh                      │
                    │  0900000004   Lễ tân           (Vũ Thị Mai)             │
                    │  0900000005   Kế toán                                   │
                    │  0912345678   Hội viên         (Nguyễn Văn An)          │
                    └─────────────────────────────────────────────────────────┘
                    """);
        };
    }

    private User taoTaiKhoan(String soDienThoai, String hoTen, UserRole vaiTro) {
        Person p = new Person();
        p.setFullName(hoTen);
        p.setPhone(soDienThoai);
        p = personRepo.save(p);

        User u = new User();
        u.setPerson(p);
        u.setUsername(soDienThoai);
        u.setPasswordHash(passwordEncoder.encode(MAT_KHAU));
        u.setPrimaryRole(vaiTro);
        u.setStatus(UserStatus.ACTIVE);
        return userRepo.save(u);
    }

    private void taoNhanVien(String soDienThoai, String hoTen, UserRole vaiTro,
                             String maNhanVien, Department phongBan, String chucDanh,
                             long luongCung, TrainerLevel bacHuanLuyen) {

        User u = taoTaiKhoan(soDienThoai, hoTen, vaiTro);

        Employee e = new Employee();
        e.setPerson(u.getPerson());
        e.setEmployeeCode(maNhanVien);
        e.setDepartment(phongBan);
        e.setPosition(chucDanh);
        e.setEmploymentType(EmploymentType.FULL_TIME);
        e.setBaseSalary(BigDecimal.valueOf(luongCung));
        e.setStartDate(LocalDate.now().minusYears(1));
        e.setStatus(EmployeeStatus.ACTIVE);

        // level chỉ có nghĩa với huấn luyện viên — cơ sở dữ liệu ràng buộc điều này
        if (phongBan == Department.TRAINING && bacHuanLuyen != null) {
            e.setLevel(bacHuanLuyen);
            e.setBio("8 năm kinh nghiệm, chứng chỉ NASM-CPT");
            e.setMaxMembers(30);
        }
        employeeRepo.save(e);
    }
}
