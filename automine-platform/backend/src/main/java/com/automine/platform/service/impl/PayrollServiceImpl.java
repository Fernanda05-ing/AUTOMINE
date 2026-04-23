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
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(request.getEmployeeId())
            .orElseThrow(() -> new ApiException("Empleado no encontrado"));
        PayrollPeriod period = payrollPeriodRepository.findById(request.getPayrollPeriodId())
            .orElseThrow(() -> new ApiException("Periodo no encontrado"));

        PayrollEntry entry = new PayrollEntry();
        entry.setEmployee(employee);
        entry.setPayrollPeriod(period);
        entry.setBaseSalary(employee.getBaseSalary());
        entry.setOvertimeHours(request.getOvertimeHours());
        entry.setOvertimeAmount(request.getOvertimeAmount());
        entry.setBonusAmount(request.getBonusAmount());
        entry.setDeductionAmount(request.getDeductionAmount());

        BigDecimal netPay = employee.getBaseSalary()
            .add(request.getOvertimeAmount())
            .add(request.getBonusAmount())
            .subtract(request.getDeductionAmount());
        entry.setNetPay(netPay);
        entry.setStatus(PayrollEntryStatus.CALCULATED);

        return toResponse(payrollEntryRepository.save(entry));
    }

    @Override
    public List<PayrollEntryResponse> listEntries() {
        return payrollEntryRepository.findByDeletedAtIsNull().stream().map(this::toResponse).toList();
    }

    private PayrollEntryResponse toResponse(PayrollEntry entry) {
        return PayrollEntryResponse.builder()
            .id(entry.getId())
            .periodCode(entry.getPayrollPeriod().getPeriodCode())
            .employeeId(entry.getEmployee().getId())
            .employeeName(entry.getEmployee().getFirstName() + " " + entry.getEmployee().getLastName())
            .baseSalary(entry.getBaseSalary())
            .overtimeAmount(entry.getOvertimeAmount())
            .bonusAmount(entry.getBonusAmount())
            .deductionAmount(entry.getDeductionAmount())
            .netPay(entry.getNetPay())
            .status(entry.getStatus().name())
            .build();
    }
}
