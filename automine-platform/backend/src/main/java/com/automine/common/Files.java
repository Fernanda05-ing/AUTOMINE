package com.automine.common;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuestas HTTP para descargas de archivos. */
public final class Files {

    public static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Files() {
    }

    public static ResponseEntity<byte[]> download(byte[] data, String filename, MediaType type, boolean inline) {
        // filename (ASCII, para clientes antiguos) + filename* (RFC 5987, conserva tildes).
        String ascii = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\x20-\\x7E]", "").replace("\"", "");
        String utf8 = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        String cd = (inline ? "inline" : "attachment") + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(type)
                .contentLength(data.length)
                .body(data);
    }

    public static ResponseEntity<byte[]> pdf(byte[] data, String filename) {
        return download(data, filename, MediaType.APPLICATION_PDF, false);
    }

    public static ResponseEntity<byte[]> xlsx(byte[] data, String filename) {
        return download(data, filename, XLSX, false);
    }
}
