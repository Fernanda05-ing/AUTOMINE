package com.automine.platform.controller;

import com.automine.platform.entity.MiningProduction;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final EnterpriseModuleService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<MiningProduction> create(@RequestBody MiningProduction production) {
        return ResponseEntity.ok(service.saveProduction(production));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','FINANCE')")
    public ResponseEntity<List<MiningProduction>> list() {
        return ResponseEntity.ok(service.listProduction());
    }
}
