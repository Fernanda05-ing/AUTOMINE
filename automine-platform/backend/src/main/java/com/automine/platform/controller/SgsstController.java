package com.automine.platform.controller;

import com.automine.platform.entity.SgsstIncident;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sgsst")
@RequiredArgsConstructor
public class SgsstController {

    private final EnterpriseModuleService service;

    @PostMapping("/incidents")
    @PreAuthorize("hasAnyRole('ADMIN','SST','SUPERVISOR')")
    public ResponseEntity<SgsstIncident> create(@RequestBody SgsstIncident incident) {
        return ResponseEntity.ok(service.saveIncident(incident));
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyRole('ADMIN','SST','RRHH')")
    public ResponseEntity<List<SgsstIncident>> list() {
        return ResponseEntity.ok(service.listIncidents());
    }
}
