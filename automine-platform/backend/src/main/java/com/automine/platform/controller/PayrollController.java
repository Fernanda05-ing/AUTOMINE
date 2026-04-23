package com.automine.platform.controller;

import com.automine.platform.dto.payroll.PayrollEntryRequest;
import com.automine.platform.dto.payroll.PayrollEntryResponse;
import com.automine.platform.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @PostMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','RRHH')")
    public ResponseEntity<PayrollEntryResponse> createEntry(@RequestBody @Valid PayrollEntryRequest request) {
        return ResponseEntity.ok(payrollService.createEntry(request));
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','RRHH')")
    public ResponseEntity<List<PayrollEntryResponse>> listEntries() {
        return ResponseEntity.ok(payrollService.listEntries());
    }
}
