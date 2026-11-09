package com.automine.common;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

/** Generador sencillo de PDF con la identidad visual de AUTOMINE. */
public class PdfBuilder {

    public static final Color PRIMARY = new Color(0xB4, 0x53, 0x09);
    private static final Color HEADER_BG = new Color(0xF5, 0xF0, 0xE8);

    private final Document document;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, PRIMARY);
    private final Font subtitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private final Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);

    public PdfBuilder(boolean landscape) {
        document = new Document(landscape ? PageSize.LETTER.rotate() : PageSize.LETTER, 50, 50, 50, 50);
        PdfWriter.getInstance(document, out);
        document.open();
    }

    public PdfBuilder() {
        this(false);
    }

    public PdfBuilder header(String companyName, String nit, String titleText) {
        // Logo institucional opcional: src/main/resources/branding/logo.png
        try (var in = PdfBuilder.class.getResourceAsStream("/branding/logo.png")) {
            if (in != null) {
                com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(in.readAllBytes());
                logo.scaleToFit(150, 70);
                document.add(logo);
            }
        } catch (Exception e) {
            // Sin logo el documento se genera igual
        }
        Paragraph company = new Paragraph(companyName, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY));
        document.add(company);
        document.add(new Paragraph("NIT " + nit, small));
        document.add(new Chunk(new LineSeparator(1f, 100f, PRIMARY, Element.ALIGN_CENTER, -4)));
        Paragraph t = new Paragraph(titleText, title);
        t.setSpacingBefore(14);
        t.setSpacingAfter(10);
        document.add(t);
        return this;
    }

    public PdfBuilder subtitle(String text) {
        Paragraph p = new Paragraph(text, subtitle);
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        document.add(p);
        return this;
    }

    public PdfBuilder paragraph(String text) {
        Paragraph p = new Paragraph(text, normal);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(16);
        p.setSpacingAfter(8);
        document.add(p);
        return this;
    }

    public PdfBuilder small(String text) {
        document.add(new Paragraph(text, small));
        return this;
    }

    public PdfBuilder space(float points) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(points);
        document.add(p);
        return this;
    }

    /** Tabla de dos columnas etiqueta / valor. */
    public PdfBuilder keyValues(List<String[]> rows) {
        PdfPTable table = new PdfPTable(new float[] { 2, 3 });
        table.setWidthPercentage(100);
        for (String[] row : rows) {
            PdfPCell k = new PdfPCell(new Phrase(row[0], bold));
            k.setBackgroundColor(HEADER_BG);
            k.setPadding(5);
            PdfPCell v = new PdfPCell(new Phrase(row[1] == null ? "" : row[1], normal));
            v.setPadding(5);
            table.addCell(k);
            table.addCell(v);
        }
        document.add(table);
        return this;
    }

    public PdfBuilder table(String[] headers, List<String[]> rows) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.BLACK);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, headerFont));
            c.setBackgroundColor(PRIMARY);
            c.setPadding(5);
            table.addCell(c);
        }
        boolean alt = false;
        for (String[] row : rows) {
            for (String v : row) {
                PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, cellFont));
                c.setPadding(4);
                if (alt) {
                    c.setBackgroundColor(HEADER_BG);
                }
                table.addCell(c);
            }
            alt = !alt;
        }
        if (rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Sin registros", cellFont));
            c.setColspan(headers.length);
            c.setPadding(6);
            table.addCell(c);
        }
        document.add(table);
        return this;
    }

    /** Bloque de firma institucional (RF025). */
    public PdfBuilder signature(String name, String role, String companyName) {
        space(30);
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(45);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        PdfPCell line = new PdfPCell(new Phrase(" "));
        line.setBorder(Rectangle.BOTTOM);
        line.setBorderColor(Color.DARK_GRAY);
        line.setFixedHeight(28);
        t.addCell(line);
        PdfPCell n = new PdfPCell(new Phrase(name + "\n" + role + "\n" + companyName, normal));
        n.setBorder(Rectangle.NO_BORDER);
        n.setPaddingTop(4);
        t.addCell(n);
        document.add(t);
        return this;
    }

    public byte[] build() {
        space(10);
        small("Documento generado por AUTOMINE el "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        document.close();
        return out.toByteArray();
    }
}
