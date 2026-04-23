package com.automine.platform.service;

import com.automine.platform.dto.employee.EmployeeRequest;
import com.automine.platform.dto.employee.EmployeeResponse;

import java.util.List;

public interface EmployeeService {
    EmployeeResponse create(EmployeeRequest request);
    List<EmployeeResponse> list();
    void retire(Long id);
}
