package com.automine.dian;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.Files;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "14. Nómina electrónica (formato DIAN)")
public class NominaElectronicaController {

    private static final String ROLES = "hasAnyRole('ADMIN','CONTADOR','RRHH')";

    private final NominaElectronicaService service;

    @Operation(summary = "XML NominaIndividual (anexo técnico DIAN) de una nómina aprobada — sin firmar ni enviar")
    @GetMapping("/Payroll/Detail/{id}/Dian")
    @PreAuthorize(ROLES)
    public ResponseEntity<byte[]> xml(@PathVariable Long id) {
        NominaElectronicaService.Documento d = service.generar(id);
        return Files.download(d.xml().getBytes(StandardCharsets.UTF_8), d.nombre(), MediaType.APPLICATION_XML, false);
    }

    @Operation(summary = "ZIP con los XML de todas las nóminas del periodo")
    @GetMapping("/Payroll/Period/{id}/Dian")
    @PreAuthorize(ROLES)
    public ResponseEntity<byte[]> zip(@PathVariable Long id) {
        return Files.download(service.zipPeriodo(id), "nomina-electronica-periodo-" + id + ".zip",
                MediaType.parseMediaType("application/zip"), false);
    }
}
