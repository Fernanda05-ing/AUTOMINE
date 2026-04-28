package com.automine.platform.dto.payroll;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PayrollEntryResponse {
    Integer id;
    String periodo;
    Integer employeeId;
    String employeeName;
    BigDecimal baseSalary;
    BigDecimal overtimeAmount;
    BigDecimal bonusAmount;
    BigDecimal deductionAmount;
    BigDecimal netPay;
    String status;
}
