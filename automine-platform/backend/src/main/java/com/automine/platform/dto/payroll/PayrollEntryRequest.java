package com.automine.platform.dto.payroll;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollEntryRequest {

    @NotNull
    private Long employeeId;

    @NotNull
    private Long payrollPeriodId;

    @NotNull
    private BigDecimal overtimeHours;

    @NotNull
    private BigDecimal overtimeAmount;

    @NotNull
    private BigDecimal bonusAmount;

    @NotNull
    private BigDecimal deductionAmount;
}
