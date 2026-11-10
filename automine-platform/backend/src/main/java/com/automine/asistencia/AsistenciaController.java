package com.automine.asistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import com.automine.asistencia.AsistenciaDtos.AnularRequest;
import com.automine.asistencia.AsistenciaDtos.EstadoRostro;
import com.automine.asistencia.AsistenciaDtos.MarcacionResponse;
import com.automine.asistencia.AsistenciaDtos.PlantillaRostro;
import com.automine.asistencia.AsistenciaDtos.ResultadoNomina;
import com.automine.asistencia.AsistenciaDtos.ResumenEmpleado;
import com.automine.common.Files;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "11. Asistencia")
public class AsistenciaController {

    /** Registra rostros y marca entradas y salidas. */
    private static final String SISO = "hasAnyRole('ADMIN','SGSST')";
    private static final String ROSTROS = "hasAnyRole('ADMIN','SGSST','RRHH')";
    private static final String CONSULTA = "hasAnyRole('ADMIN','SGSST','RRHH','CONTADOR','GERENTE','SUPERVISOR')";

    private final AsistenciaService service;

    // ------------------------------ Rostros ------------------------------

    @Operation(summary = "Registrar el rostro de un trabajador (requiere su autorización, Ley 1581)")
    @PostMapping(value = "/Employee/{id}/Face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ROSTROS)
    public EstadoRostro registrarRostro(@PathVariable Long id, @RequestParam String plantilla,
            @RequestParam(required = false) String version, @RequestParam(defaultValue = "false") boolean autoriza,
            @RequestPart(name = "foto", required = false) MultipartFile foto) {
        return service.registrarRostro(id, plantilla, version, autoriza, foto);
    }

    @DeleteMapping("/Employee/{id}/Face")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ROSTROS)
    public void borrarRostro(@PathVariable Long id) {
        service.borrarRostro(id);
    }

    @GetMapping("/Employee/{id}/Face/Photo")
    @PreAuthorize(ROSTROS)
    public ResponseEntity<byte[]> fotoRostro(@PathVariable Long id) {
        return Files.download(service.fotoRostro(id), "rostro-" + id + ".jpg", MediaType.IMAGE_JPEG, true);
    }

    @Operation(summary = "Plantillas faciales de los trabajadores activos (para reconocer sin conexión)")
    @GetMapping("/Faces")
    @PreAuthorize(SISO)
    public List<PlantillaRostro> plantillas() {
        return service.plantillas();
    }

    @GetMapping("/Faces/Status")
    @PreAuthorize(ROSTROS)
    public List<EstadoRostro> estado() {
        return service.estadoRostros();
    }

    @GetMapping("/Faces/Consent")
    public Map<String, Object> autorizacion() {
        return Map.of("texto", AsistenciaService.AUTORIZACION, "umbral", service.umbral());
    }

    // ------------------------------ Marcaciones ------------------------------

    @Operation(summary = "Marcar entrada o salida (foto + similitud calculada en el celular, o manual con motivo)")
    @PostMapping(value = "/Attendance/Mark", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SISO)
    public MarcacionResponse marcar(@RequestParam Long empleadoId, @RequestParam String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHora,
            @RequestParam(required = false) BigDecimal similitud,
            @RequestParam(required = false) String observacion,
            @RequestParam(required = false) String dispositivoId,
            @RequestPart(name = "foto", required = false) MultipartFile foto) {
        return service.marcar(empleadoId, tipo, fechaHora, similitud, observacion, dispositivoId, foto);
    }

    @GetMapping("/Attendance")
    @PreAuthorize(CONSULTA)
    public List<MarcacionResponse> delDia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long empleadoId) {
        return service.delDia(fecha, empleadoId);
    }

    @GetMapping("/Attendance/{id}/Photo")
    @PreAuthorize(CONSULTA)
    public ResponseEntity<byte[]> foto(@PathVariable Long id) {
        return Files.download(service.fotoMarcacion(id), "marcacion-" + id + ".jpg", MediaType.IMAGE_JPEG, true);
    }

    @PutMapping("/Attendance/{id}/Cancel")
    @PreAuthorize(ROSTROS)
    public MarcacionResponse anular(@PathVariable Long id, @Valid @RequestBody AnularRequest req) {
        return service.anular(id, req.motivo());
    }

    @Operation(summary = "Horas trabajadas y extra del mes por trabajador")
    @GetMapping("/Attendance/Summary")
    public List<ResumenEmpleado> resumen(@RequestParam int anio, @RequestParam int mes,
            @RequestParam(required = false) Long empleadoId) {
        if (empleadoId == null) {
            com.automine.security.UsuarioActual u = com.automine.security.UsuarioActual.get();
            if (!u.tieneRol("ADMIN", "SGSST", "RRHH", "CONTADOR", "GERENTE", "SUPERVISOR")) {
                return u.empleadoId() == null ? List.of() : service.resumen(anio, mes, u.empleadoId());
            }
        }
        return service.resumen(anio, mes, empleadoId);
    }

    @Operation(summary = "Pasar las horas extra del mes a las nóminas abiertas")
    @PostMapping("/Attendance/ApplyToPayroll")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR','RRHH')")
    public ResultadoNomina aplicar(@RequestParam int anio, @RequestParam int mes) {
        return service.aplicarANomina(anio, mes);
    }
}
