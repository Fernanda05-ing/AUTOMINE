package com.automine.incapacidades;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.Files;
import com.automine.incapacidades.IncapacidadDtos.AnulacionRequest;
import com.automine.incapacidades.IncapacidadDtos.IncapacidadRequest;
import com.automine.incapacidades.IncapacidadDtos.IncapacidadResponse;
import com.automine.incapacidades.IncapacidadDtos.ResumenIncapacidades;
import com.automine.incapacidades.IncapacidadDtos.RevisionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "9. Incapacidades")
public class IncapacidadController {

    private static final String REVISAN = "hasAnyRole('ADMIN','SGSST','RRHH')";

    private final IncapacidadService service;

    @Operation(summary = "Registrar incapacidad con su certificado (multipart: campos + archivo 'soporte')")
    @PostMapping(value = "/Disability", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IncapacidadResponse registrar(@RequestParam(required = false) Long empleadoId,
            @RequestParam String tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) Boolean prorroga,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String numeroCertificado,
            @RequestParam(required = false) String observaciones,
            @RequestPart(name = "soporte", required = false) MultipartFile soporte) {
        return service.registrar(new IncapacidadRequest(empleadoId, tipo, fechaInicio, fechaFin, prorroga, entidad,
                numeroCertificado, observaciones), soporte);
    }

    @Operation(summary = "Listar incapacidades (el trabajador solo ve las suyas)")
    @GetMapping("/Disabilities")
    public List<IncapacidadResponse> listar(@RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) String estado) {
        return service.listar(empleadoId, estado);
    }

    @GetMapping("/Disabilities/Summary")
    @PreAuthorize(REVISAN)
    public ResumenIncapacidades resumen() {
        return service.resumen();
    }

    @GetMapping("/Disability/{id}")
    public IncapacidadResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @Operation(summary = "Cambiar el certificado (pendiente o rechazada; si estaba rechazada vuelve a revisión)")
    @PutMapping(value = "/Disability/{id}/Support", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IncapacidadResponse cambiarSoporte(@PathVariable Long id,
            @RequestPart(name = "soporte", required = false) MultipartFile soporte) {
        return service.reemplazarSoporte(id, soporte);
    }

    @Operation(summary = "Descargar el certificado (trabajador, SG-SST, Talento Humano)")
    @GetMapping("/Disability/{id}/Support")
    public ResponseEntity<byte[]> soporte(@PathVariable Long id) {
        Incapacidad.Soporte s = service.soporte(id);
        return Files.download(s.getDatos(), s.getNombre(), MediaType.parseMediaType(s.getContentType()), true);
    }

    @Operation(summary = "Aprobar o rechazar (SG-SST, Talento Humano)")
    @PutMapping("/Disability/{id}/Review")
    @PreAuthorize(REVISAN)
    public IncapacidadResponse revisar(@PathVariable Long id, @Valid @RequestBody RevisionRequest req) {
        return service.revisar(id, req);
    }

    @Operation(summary = "Anular una incapacidad registrada por error")
    @PutMapping("/Disability/{id}/Cancel")
    @PreAuthorize(REVISAN)
    public IncapacidadResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest req) {
        return service.anular(id, req);
    }
}
