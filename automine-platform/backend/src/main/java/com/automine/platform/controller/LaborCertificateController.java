package com.automine.platform.controller;

import com.automine.platform.entity.LaborCertificate;
import com.automine.platform.service.EnterpriseModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class LaborCertificateController {

    private final EnterpriseModuleService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public ResponseEntity<LaborCertificate> create(@RequestBody LaborCertificate certificate) {
        return ResponseEntity.ok(service.saveCertificate(certificate));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH')")
    public ResponseEntity<List<LaborCertificate>> list() {
        return ResponseEntity.ok(service.listCertificates());
    }

    @GetMapping("/public/validate/{validationCode}")
    public ResponseEntity<LaborCertificate> validate(@PathVariable String validationCode) {
        return ResponseEntity.ok(service.findCertificateByValidationCode(validationCode));
    }
}
