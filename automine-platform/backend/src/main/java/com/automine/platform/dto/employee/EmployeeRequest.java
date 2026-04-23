package com.automine.platform.dto.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeRequest {

    @NotBlank
    private String employeeCode;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String documentType;

    @NotBlank
    private String documentNumber;

    @NotBlank
    private String position;

    @NotNull
    private BigDecimal baseSalary;

    @NotNull
    private LocalDate hireDate;
}
