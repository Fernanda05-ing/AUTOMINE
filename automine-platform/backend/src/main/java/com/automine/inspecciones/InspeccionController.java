package com.automine.inspecciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.Files;
import com.automine.inspecciones.InspeccionService.InspeccionResponse;
import com.automine.inspecciones.InspeccionService.Punto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "13. Inspecciones preoperacionales")
public class InspeccionController {

    private static final String INSPECCIONAN = "hasAnyRole('ADMIN','SGSST','SUPERVISOR','ALMACENISTA')";
    private static final String CONSULTAN = "hasAnyRole('ADMIN','SGSST','SUPERVISOR','ALMACENISTA','GERENTE')";

    private final InspeccionService service;

    @GetMapping("/Inspections/Checklists")
    @PreAuthorize(CONSULTAN)
    public Map<String, List<Punto>> listas() {
        return InspeccionService.LISTAS;
    }

    @Operation(summary = "Registrar inspección preoperacional (items en JSON + fotos)")
    @PostMapping(value = "/Inspection", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(INSPECCIONAN)
    public InspeccionResponse registrar(@RequestParam(required = false) Long maquinariaId,
            @RequestParam(required = false) String equipo, @RequestParam String tipo,
            @RequestParam(required = false) BigDecimal horometro, @RequestParam(required = false) Long operadorId,
            @RequestParam(required = false) String observaciones, @RequestParam String items,
            @RequestPart(name = "fotos", required = false) List<MultipartFile> fotos) {
        return service.registrar(maquinariaId, equipo, tipo, horometro, operadorId, observaciones, items, fotos);
    }

    @GetMapping("/Inspections")
    @PreAuthorize(CONSULTAN)
    public List<InspeccionResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long maquinariaId) {
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        return service.listar(desde == null ? h.minusDays(30) : desde, h, maquinariaId);
    }

    @GetMapping("/Inspection/{id}")
    @PreAuthorize(CONSULTAN)
    public InspeccionResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/Inspection/{id}/Photo/{fotoId}")
    @PreAuthorize(CONSULTAN)
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @PathVariable Long fotoId) {
        Inspeccion.Foto f = service.foto(id, fotoId);
        return Files.download(f.getDatos(), "inspeccion-" + id + "-" + fotoId + ".jpg",
                MediaType.parseMediaType(f.getContentType()), true);
    }
}
