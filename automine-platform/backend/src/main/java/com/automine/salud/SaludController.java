package com.automine.salud;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.Files;
import com.automine.salud.SaludService.EstadoExamen;
import com.automine.salud.SaludService.ExamenRequest;
import com.automine.salud.SaludService.ExamenResponse;
import com.automine.salud.SaludService.Indicadores;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "12. Salud ocupacional")
public class SaludController {

    private static final String GESTIONAN = "hasAnyRole('ADMIN','SGSST','RRHH')";
    private static final String CONSULTAN = "hasAnyRole('ADMIN','SGSST','RRHH','GERENTE','SUPERVISOR')";

    private final SaludService service;

    @Operation(summary = "Registrar evaluación médica ocupacional (concepto de aptitud, sin diagnóstico)")
    @PostMapping(value = "/MedicalExam", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTIONAN)
    public ExamenResponse registrar(@RequestParam Long empleadoId, @RequestParam String tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha, @RequestParam String concepto,
            @RequestParam(required = false) String restricciones, @RequestParam(required = false) String recomendaciones,
            @RequestParam(required = false) String ips, @RequestParam(required = false) String medico,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate proximaFecha,
            @RequestPart(name = "soporte", required = false) MultipartFile soporte) {
        return service.registrar(new ExamenRequest(empleadoId, tipo, fecha, concepto, restricciones, recomendaciones, ips,
                medico, proximaFecha), soporte);
    }

    @GetMapping("/MedicalExams")
    public List<ExamenResponse> listar(@RequestParam(required = false) Long empleadoId) {
        return service.listar(empleadoId);
    }

    @GetMapping("/MedicalExams/Status")
    @PreAuthorize(CONSULTAN)
    public List<EstadoExamen> estado() {
        return service.estado();
    }

    @GetMapping("/MedicalExam/{id}/Support")
    public ResponseEntity<byte[]> soporte(@PathVariable Long id) {
        ExamenMedico x = service.soporte(id);
        return Files.download(x.getSoporte(), x.getSoporteNombre(), MediaType.parseMediaType(x.getSoporteTipo()), true);
    }

    @Operation(summary = "Indicadores del SG-SST del año (Resolución 0312 de 2019)")
    @GetMapping("/Sst/Indicators")
    @PreAuthorize(CONSULTAN)
    public Indicadores indicadores(@RequestParam int anio) {
        return service.indicadores(anio);
    }
}
