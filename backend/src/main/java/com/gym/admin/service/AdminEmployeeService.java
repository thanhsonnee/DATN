package com.gym.admin.service;

import com.gym.admin.api.dto.CreateEmployeeAccountRequest;
import com.gym.admin.api.dto.EmployeeAccountResponse;
import com.gym.admin.api.dto.EmployeeSummaryResponse;
import com.gym.common.exception.ApiException;
import com.gym.common.util.CodeGenerator;
import com.gym.common.util.TempPasswordGenerator;
import com.gym.identity.domain.*;
import com.gym.identity.repository.EmployeeRepository;
import com.gym.identity.repository.PersonRepository;
import com.gym.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Admin tạo tài khoản cho nhân viên (PT/Sale/Lễ tân/Kế toán).
 *
 * <p>Các vai trò này KHÔNG tự đăng ký được — {@code /auth/register} công khai
 * luôn gán cứng MEMBER (xem {@code AuthService.register}). Trước tính năng
 * này, cách duy nhất tạo tài khoản nhân viên là {@code DemoAccountSeeder} chạy
 * lúc khởi động, chỉ dùng để có dữ liệu thử.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEmployeeService {

    /** CHỈ 4 vai trò này được tạo qua đường Admin — không cho tạo thêm ADMIN/MEMBER ở đây. */
    private static final Set<UserRole> VAI_TRO_NHAN_SU =
            EnumSet.of(UserRole.TRAINER, UserRole.SALE, UserRole.RECEPTIONIST, UserRole.ACCOUNTANT);

    private final PersonRepository personRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final CodeGenerator codeGenerator;
    private final TempPasswordGenerator tempPasswordGenerator;

    @Transactional
    public EmployeeAccountResponse taoTaiKhoanNhanVien(CreateEmployeeAccountRequest req) {
        if (!VAI_TRO_NHAN_SU.contains(req.role())) {
            throw ApiException.badRequest("INVALID_ROLE",
                    "Chỉ tạo được tài khoản cho vai trò TRAINER, SALE, RECEPTIONIST hoặc ACCOUNTANT");
        }
        if (personRepo.existsByPhoneAndDeletedAtIsNull(req.phone())) {
            throw ApiException.conflict("PHONE_TAKEN", "Số điện thoại này đã được đăng ký");
        }
        if (req.email() != null && !req.email().isBlank()
                && personRepo.existsByEmailAndDeletedAtIsNull(req.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "Email này đã được sử dụng");
        }
        if (userRepo.existsByUsernameAndDeletedAtIsNull(req.phone())) {
            throw ApiException.conflict("USERNAME_TAKEN", "Tài khoản này đã tồn tại");
        }

        Person person = new Person();
        person.setFullName(req.fullName().trim());
        person.setPhone(req.phone());
        person.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
        person = personRepo.save(person);

        // Sinh mật khẩu tạm — CHỈ tồn tại trong bộ nhớ ở lời gọi này, không log,
        // không lưu dạng chữ. Trả về đúng một lần trong response cho Admin.
        String tempPassword = tempPasswordGenerator.generate();

        User user = new User();
        user.setPerson(person);
        user.setUsername(req.phone());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setPrimaryRole(req.role());
        user.setStatus(UserStatus.ACTIVE);
        user = userRepo.save(user);

        Employee employee = new Employee();
        employee.setPerson(person);
        employee.setEmployeeCode(codeGenerator.nextEmployeeCode());
        employee.setDepartment(mapDepartment(req.role()));
        employee.setPosition(req.position());
        employee.setEmploymentType(req.employmentType());
        employee.setBaseSalary(req.baseSalary());
        employee.setStartDate(req.startDate() != null ? req.startDate() : LocalDate.now());
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employeeRepo.save(employee);

        log.info("Admin tạo tài khoản nhân viên: employeeId={} employeeCode={} role={}",
                employee.getId(), employee.getEmployeeCode(), req.role());

        return EmployeeAccountResponse.of(employee, user, tempPassword);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummaryResponse> danhSachNhanVien() {
        return employeeRepo.findByDeletedAtIsNull().stream()
                .map(EmployeeSummaryResponse::from)
                .toList();
    }

    /** department phải khớp 1-1 với primary_role — ràng buộc đã có ở tầng CSDL (chk_emp_level_dept). */
    private Department mapDepartment(UserRole role) {
        return switch (role) {
            case TRAINER -> Department.TRAINING;
            case SALE -> Department.SALES;
            case RECEPTIONIST -> Department.FRONT_DESK;
            case ACCOUNTANT -> Department.ACCOUNTING;
            default -> throw ApiException.badRequest("INVALID_ROLE", "Vai trò không hợp lệ cho nhân sự");
        };
    }
}
