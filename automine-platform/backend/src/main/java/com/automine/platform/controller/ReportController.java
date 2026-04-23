package com.automine.platform.controller;

import com.automine.platform.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/payroll.pdf")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','RRHH')")
    public ResponseEntity<byte[]> payrollPdf() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payroll-report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(reportService.generatePayrollPdf());
    }

    @GetMapping("/accounting.xlsx")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<byte[]> accountingExcel() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=accounting-report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(reportService.generateAccountingExcel());
    }
}
