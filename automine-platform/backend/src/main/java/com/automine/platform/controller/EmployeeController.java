package com.automine.platform.controller;

import com.automine.platform.dto.employee.EmployeeRequest;
import com.automine.platform.dto.employee.EmployeeResponse;
import com.automine.platform.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public ResponseEntity<EmployeeResponse> create(@RequestBody @Valid EmployeeRequest request) {
        return ResponseEntity.ok(employeeService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ResponseEntity<List<EmployeeResponse>> list() {
        return ResponseEntity.ok(employeeService.list());
    }

    @PatchMapping("/{id}/retire")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public ResponseEntity<Void> retire(@PathVariable Long id) {
        employeeService.retire(id);
        return ResponseEntity.noContent().build();
    }
}
