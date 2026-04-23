package com.automine.platform.controller;

import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EnterpriseModuleService service;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','SUPERVISOR')")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(service.dashboardSummary());
    }
}
