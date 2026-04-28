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
        employee.setCedula(request.getDocumentNumber());
        employee.setNombres(request.getFirstName());
        employee.setApellidos(request.getLastName());
        employee.setTelefono(request.getPhone());
        employee.setDireccion(request.getAddress());
        employee.setCargo(request.getPosition());
        employee.setArea(request.getArea());
        employee.setSalario(request.getBaseSalary());
        employee.setFechaIngreso(request.getHireDate());
        employee.setEstado(EmployeeStatus.ACTIVO);

        return toResponse(employeeRepository.save(employee));
    }

    @Override
    public List<EmployeeResponse> list() {
        return employeeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public void retire(Integer id) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new ApiException("Empleado no encontrado"));
        employee.setFechaRetiro(LocalDate.now());
        employee.setEstado(EmployeeStatus.RETIRADO);
        employeeRepository.save(employee);
    }

    private EmployeeResponse toResponse(Employee employee) {
        return EmployeeResponse.builder()
            .id(employee.getEmpleadoId())
            .employeeCode(employee.getCedula())
            .fullName(employee.getNombres() + " " + employee.getApellidos())
            .documentNumber(employee.getCedula())
            .position(employee.getCargo())
            .baseSalary(employee.getSalario())
            .hireDate(employee.getFechaIngreso())
            .status(employee.getEstado().name())
            .build();
    }
}
