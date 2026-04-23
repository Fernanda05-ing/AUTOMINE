package com.automine.platform.repository;

import com.automine.platform.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByDeletedAtIsNull();
    Optional<Employee> findByIdAndDeletedAtIsNull(Long id);
}
