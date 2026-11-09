package com.automine.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** RF046: exportacion a Excel (.xlsx). Cada llamada a sheet() agrega una hoja. */
public class ExcelBuilder {

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final CellStyle headerStyle;
    private final CellStyle moneyStyle;
    private final CellStyle dateStyle;

    public ExcelBuilder() {
        headerStyle = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
    }

    public ExcelBuilder sheet(String name, String[] headers, List<Object[]> rows) {
        Sheet sheet = workbook.createSheet(name);
        Row h = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (Object[] values : rows) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < values.length; i++) {
                Cell c = row.createCell(i);
                Object v = values[i];
                if (v == null) {
                    c.setBlank();
                } else if (v instanceof BigDecimal bd) {
                    c.setCellValue(bd.doubleValue());
                    c.setCellStyle(moneyStyle);
                } else if (v instanceof Number n) {
                    c.setCellValue(n.doubleValue());
                } else if (v instanceof LocalDate d) {
                    c.setCellValue(d);
                    c.setCellStyle(dateStyle);
                } else if (v instanceof LocalDateTime dt) {
                    c.setCellValue(dt.toLocalDate());
                    c.setCellStyle(dateStyle);
                } else if (v instanceof Boolean b) {
                    c.setCellValue(b ? "Sí" : "No");
                } else {
                    c.setCellValue(v.toString());
                }
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(0, 1);
        return this;
    }

    public byte[] build() {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
