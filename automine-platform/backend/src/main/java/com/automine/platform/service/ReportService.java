package com.automine.platform.service;

public interface ReportService {
    byte[] generatePayrollPdf();
    byte[] generateAccountingExcel();
}
