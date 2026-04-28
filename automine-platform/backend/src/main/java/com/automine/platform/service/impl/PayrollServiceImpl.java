package com.automine.platform.service.impl;

import com.automine.platform.dto.payroll.PayrollEntryRequest;
import com.automine.platform.dto.payroll.PayrollEntryResponse;
import com.automine.platform.entity.Employee;
import com.automine.platform.entity.PayrollEntry;
import com.automine.platform.entity.PayrollPeriod;
import com.automine.platform.entity.enums.PayrollEntryStatus;
import com.automine.platform.exception.ApiException;
import com.automine.platform.repository.EmployeeRepository;
import com.automine.platform.repository.PayrollEntryRepository;
import com.automine.platform.repository.PayrollPeriodRepository;
import com.automine.platform.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private final PayrollEntryRepository payrollEntryRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public PayrollEntryResponse createEntry(PayrollEntryRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
            .orElseThrow(() -> new ApiException("Empleado no encontrado"));

        PayrollEntry entry = new PayrollEntry();
        entry.setEmployee(employee);
        entry.setPeriodo(request.getPeriodo());
        entry.setSalarioBase(employee.getSalario());
        entry.setHorasExtras(request.getOvertimeHours());
        entry.setBonificaciones(request.getBonusAmount());
        entry.setDescuentos(request.getDeductionAmount());

        BigDecimal netPay = employee.getSalario()
            .add(request.getOvertimeAmount() != null ? request.getOvertimeAmount() : BigDecimal.ZERO)
            .add(request.getBonusAmount() != null ? request.getBonusAmount() : BigDecimal.ZERO)
            .subtract(request.getDeductionAmount() != null ? request.getDeductionAmount() : BigDecimal.ZERO);
        entry.setNetoPagar(netPay);
        entry.setEstado(PayrollEntryStatus.GENERADA);

        return toResponse(payrollEntryRepository.save(entry));
    }

    @Override
    public List<PayrollEntryResponse> listEntries() {
        return payrollEntryRepository.findAll().stream().map(this::toResponse).toList();
    }

    private PayrollEntryResponse toResponse(PayrollEntry entry) {
        return PayrollEntryResponse.builder()
            .id(entry.getNominaId())
            .periodo(entry.getPeriodo())
            .employeeId(entry.getEmployee().getEmpleadoId())
            .employeeName(entry.getEmployee().getNombres() + " " + entry.getEmployee().getApellidos())
            .baseSalary(entry.getSalarioBase())
            .overtimeAmount(entry.getBonificaciones()) // assuming bonificaciones is bonus
            .bonusAmount(entry.getBonificaciones())
            .deductionAmount(entry.getDescuentos())
            .netPay(entry.getNetoPagar())
            .status(entry.getEstado().name())
            .build();
    }
}
