package com.automine.platform.controller;

import com.automine.platform.entity.AccountingTransaction;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final EnterpriseModuleService service;

    @PostMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<AccountingTransaction> create(@RequestBody AccountingTransaction transaction) {
        return ResponseEntity.ok(service.saveTransaction(transaction));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<List<AccountingTransaction>> list() {
        return ResponseEntity.ok(service.listTransactions());
    }
}
