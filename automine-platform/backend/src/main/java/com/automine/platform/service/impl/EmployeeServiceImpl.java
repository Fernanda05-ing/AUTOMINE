package com.automine.platform.service.impl;

import com.automine.platform.dto.employee.EmployeeRequest;
import com.automine.platform.dto.employee.EmployeeResponse;
import com.automine.platform.entity.Employee;
import com.automine.platform.entity.enums.EmployeeStatus;
import com.automine.platform.exception.ApiException;
import com.automine.platform.repository.EmployeeRepository;
import com.automine.platform.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Override
    public EmployeeResponse create(EmployeeRequest request) {
        Employee employee = new Employee();
        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setDocumentType(request.getDocumentType());
        employee.setDocumentNumber(request.getDocumentNumber());
        employee.setPosition(request.getPosition());
        employee.setBaseSalary(request.getBaseSalary());
        employee.setHireDate(request.getHireDate());
        employee.setStatus(EmployeeStatus.ACTIVE);

        return toResponse(employeeRepository.save(employee));
    }

    @Override
    public List<EmployeeResponse> list() {
        return employeeRepository.findByDeletedAtIsNull().stream().map(this::toResponse).toList();
    }

    @Override
    public void retire(Long id) {
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException("Empleado no encontrado"));
        employee.setTerminationDate(LocalDate.now());
        employee.setStatus(EmployeeStatus.RETIRED);
        employeeRepository.save(employee);
    }

    private EmployeeResponse toResponse(Employee employee) {
        return EmployeeResponse.builder()
            .id(employee.getId())
            .employeeCode(employee.getEmployeeCode())
            .fullName(employee.getFirstName() + " " + employee.getLastName())
            .documentNumber(employee.getDocumentNumber())
            .position(employee.getPosition())
            .baseSalary(employee.getBaseSalary())
            .hireDate(employee.getHireDate())
            .status(employee.getStatus().name())
            .build();
    }
}
