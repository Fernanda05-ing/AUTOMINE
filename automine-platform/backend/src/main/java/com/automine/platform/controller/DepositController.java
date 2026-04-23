package com.automine.platform.controller;

import com.automine.platform.entity.BankDeposit;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
public class DepositController {

    private final EnterpriseModuleService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<BankDeposit> create(@RequestBody BankDeposit deposit) {
        return ResponseEntity.ok(service.saveDeposit(deposit));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','RRHH')")
    public ResponseEntity<List<BankDeposit>> list() {
        return ResponseEntity.ok(service.listDeposits());
    }
}
