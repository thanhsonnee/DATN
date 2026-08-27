package com.gym.identity.repository;

import com.gym.identity.domain.Department;
import com.gym.identity.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByPersonIdAndDeletedAtIsNull(Long personId);

    Optional<Employee> findByEmployeeCodeAndDeletedAtIsNull(String employeeCode);

    List<Employee> findByDepartmentAndDeletedAtIsNull(Department department);
}
