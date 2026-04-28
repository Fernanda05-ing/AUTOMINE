package com.automine.platform.dto.employee;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class EmployeeResponse {
    Integer id;
    String employeeCode;
    String fullName;
    String documentNumber;
    String position;
    BigDecimal baseSalary;
    LocalDate hireDate;
    String status;
}
