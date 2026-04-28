package com.automine.platform.service.impl;

import com.automine.platform.entity.AccountingTransaction;
import com.automine.platform.entity.PayrollEntry;
import com.automine.platform.repository.AccountingTransactionRepository;
import com.automine.platform.repository.PayrollEntryRepository;
import com.automine.platform.service.ReportService;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final PayrollEntryRepository payrollEntryRepository;
    private final AccountingTransactionRepository accountingTransactionRepository;

    @Override
    public byte[] generatePayrollPdf() {
        List<PayrollEntry> entries = payrollEntryRepository.findAll();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Reporte de Nomina AUTOMINE"));
            document.add(new Paragraph("Total registros: " + entries.size()));
            for (PayrollEntry entry : entries) {
                document.add(new Paragraph("Empleado ID " + entry.getEmployee().getEmpleadoId() + " - Neto: " + entry.getNetoPagar()));
            }
            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible generar PDF", ex);
        }
    }

    @Override
    public byte[] generateAccountingExcel() {
        List<AccountingTransaction> transactions = accountingTransactionRepository.findAll();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Contabilidad");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Fecha");
            header.createCell(1).setCellValue("Tipo");
            header.createCell(2).setCellValue("Categoria");
            header.createCell(3).setCellValue("Monto");

            int rowIndex = 1;
            for (AccountingTransaction tx : transactions) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(tx.getTransactionDate().toString());
                row.createCell(1).setCellValue(tx.getTransactionType());
                row.createCell(2).setCellValue(tx.getCategory());
                row.createCell(3).setCellValue(tx.getAmount().doubleValue());
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible generar Excel", ex);
        }
    }
}
