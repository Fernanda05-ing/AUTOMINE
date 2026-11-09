package com.automine.produccion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.Roles;
import com.automine.produccion.ProduccionDtos.AsignacionRequest;
import com.automine.produccion.ProduccionDtos.FrenteRequest;
import com.automine.produccion.ProduccionDtos.Indicadores;
import com.automine.produccion.ProduccionDtos.ProduccionRequest;
import com.automine.produccion.ProduccionDtos.ProduccionResponse;
import com.automine.produccion.ProduccionDtos.ReporteDiario;
import com.automine.produccion.ProduccionDtos.TurnoEmpleado;
import com.automine.security.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "7. Producción minera")
public class ProduccionController {

    private static final String LECTURA = "hasAnyRole('ADMIN','SUPERVISOR','GERENTE')";
    private static final String ESCRITURA = "hasAnyRole('ADMIN','SUPERVISOR')";

    private final ProduccionService service;

    @Operation(summary = "RegisterProduction - registrar producción del turno")
    @PostMapping("/Production")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    public ProduccionResponse create(@Valid @RequestBody ProduccionRequest req) {
        return service.registrar(req);
    }

    @Operation(summary = "ListProduction - consultar producción")
    @GetMapping("/Production")
    @PreAuthorize(LECTURA)
    public List<ProduccionResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long frenteId) {
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        LocalDate d = desde == null ? h.minusDays(30) : desde;
        return service.listar(d, h, frenteId);
    }

    @GetMapping("/Production/{id}")
    @PreAuthorize(LECTURA)
    public ProduccionResponse get(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/Production/{id}")
    @PreAuthorize(ESCRITURA)
    public ProduccionResponse update(@PathVariable Long id, @Valid @RequestBody ProduccionRequest req) {
        return service.editar(id, req);
    }

    @DeleteMapping("/Production/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(ESCRITURA)
    public void delete(@PathVariable Long id) {
        service.eliminar(id);
    }

    @Operation(summary = "Asignar personal al turno")
    @PutMapping("/Production/{id}/Assignments")
    @PreAuthorize(ESCRITURA)
    public ProduccionResponse assign(@PathVariable Long id, @Valid @RequestBody List<@Valid AsignacionRequest> req) {
        return service.asignarPersonal(id, req);
    }

    @Operation(summary = "Reporte diario de producción")
    @GetMapping("/Production/DailyReport")
    @PreAuthorize(LECTURA)
    public ReporteDiario daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.reporteDiario(fecha == null ? LocalDate.now() : fecha);
    }

    @Operation(summary = "Indicadores de rendimiento")
    @GetMapping("/Production/Indicators")
    @PreAuthorize(LECTURA)
    public Indicadores indicators(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.indicadores(desde, hasta);
    }

    @Operation(summary = "Turnos asignados a un empleado")
    @GetMapping("/Shifts/{idEmpleado}")
    public List<TurnoEmpleado> shifts(@PathVariable Long idEmpleado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.SUPERVISOR, Roles.GERENTE, Roles.RRHH);
        LocalDate h = hasta == null ? LocalDate.now().plusDays(30) : hasta;
        LocalDate d = desde == null ? LocalDate.now().minusDays(60) : desde;
        return service.turnosEmpleado(idEmpleado, d, h);
    }

    @GetMapping("/Fronts")
    public List<FrenteTrabajo> fronts() {
        return service.frentes();
    }

    @PostMapping("/Fronts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    public FrenteTrabajo createFront(@Valid @RequestBody FrenteRequest req) {
        return service.guardarFrente(null, req);
    }

    @PutMapping("/Fronts/{id}")
    @PreAuthorize(ESCRITURA)
    public FrenteTrabajo updateFront(@PathVariable Long id, @Valid @RequestBody FrenteRequest req) {
        return service.guardarFrente(id, req);
    }
}
