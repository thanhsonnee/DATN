package com.gym.identity.service;

import com.gym.identity.api.dto.TrainerResponse;
import com.gym.identity.domain.Department;
import com.gym.identity.domain.EmployeeStatus;
import com.gym.identity.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tra cứu nhân sự cho các màn hình chọn-bằng-tên (không gõ tay mã số).
 *
 * DTO phải được dựng ở đây, bên trong transaction — {@code Person} được lazy-load
 * qua {@code Employee.person}, nên nếu controller tự map entity -> DTO sau khi
 * repository trả về thì session Hibernate đã đóng, ném LazyInitializationException.
 */
@Service
@RequiredArgsConstructor
public class EmployeeLookupService {

    private final EmployeeRepository employeeRepo;

    @Transactional(readOnly = true)
    public List<TrainerResponse> trainers() {
        // level chỉ được gán cho huấn luyện viên thật (xem DemoAccountSeeder) — nhân sự
        // quản lý/hành chính dù cùng phòng ban TRAINING vẫn không có level, nên loại ra.
        return employeeRepo.findByDepartmentAndDeletedAtIsNull(Department.TRAINING).stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE && e.getLevel() != null)
                .map(TrainerResponse::from)
                .toList();
    }
}
