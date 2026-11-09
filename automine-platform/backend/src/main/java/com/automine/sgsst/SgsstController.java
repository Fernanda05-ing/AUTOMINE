package com.automine.sgsst;

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
import com.automine.security.UsuarioActual;
import com.automine.sgsst.SgsstDtos.AsistenciaRequest;
import com.automine.sgsst.SgsstDtos.CapacitacionRequest;
import com.automine.sgsst.SgsstDtos.CapacitacionResponse;
import com.automine.sgsst.SgsstDtos.EppRequest;
import com.automine.sgsst.SgsstDtos.EppResponse;
import com.automine.sgsst.SgsstDtos.EstadoCapacitacionRequest;
import com.automine.sgsst.SgsstDtos.EventoRequest;
import com.automine.sgsst.SgsstDtos.EventoResponse;
import com.automine.sgsst.SgsstDtos.ResumenSst;
import com.automine.sgsst.SgsstDtos.RiesgoRequest;
import com.automine.sgsst.SgsstDtos.SeguimientoRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "8. SG-SST")
public class SgsstController {

    private static final String SST = "hasAnyRole('ADMIN','SGSST')";
    private static final String LECTURA = "hasAnyRole('ADMIN','SGSST','GERENTE','SUPERVISOR','RRHH')";

    private final SgsstService service;

    @Operation(summary = "AddAccident - registrar accidente o incidente")
    @PostMapping("/Accident")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SGSST','SUPERVISOR')")
    public EventoResponse addAccident(@Valid @RequestBody EventoRequest req) {
        return service.registrarEvento(req);
    }

    @GetMapping("/Accidents")
    @PreAuthorize(LECTURA)
    public List<EventoResponse> accidents(@RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        return service.eventos(tipo, desde == null ? h.minusYears(1) : desde, h);
    }

    @GetMapping("/Accident/{id}")
    @PreAuthorize(LECTURA)
    public EventoResponse accident(@PathVariable Long id) {
        return service.evento(id);
    }

    @PutMapping("/Accident/{id}")
    @PreAuthorize(SST)
    public EventoResponse followUp(@PathVariable Long id, @Valid @RequestBody SeguimientoRequest req) {
        return service.seguimiento(id, req);
    }

    @GetMapping("/Accidents/Employee/{idEmpleado}")
    public List<EventoResponse> accidentsByEmployee(@PathVariable Long idEmpleado) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.SGSST, Roles.GERENTE, Roles.RRHH);
        return service.eventosEmpleado(idEmpleado);
    }

    @Operation(summary = "Registrar entrega de EPP")
    @PostMapping("/Epp")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SST)
    public EppResponse addEpp(@Valid @RequestBody EppRequest req) {
        return service.entregarEpp(req);
    }

    @GetMapping("/Epp")
    @PreAuthorize(LECTURA)
    public List<EppResponse> epp(@RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        return service.entregas(empleadoId, desde == null ? h.minusYears(1) : desde, h);
    }

    @GetMapping("/Epp/Employee/{idEmpleado}")
    public List<EppResponse> eppByEmployee(@PathVariable Long idEmpleado) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.SGSST, Roles.GERENTE, Roles.RRHH);
        return service.entregas(idEmpleado, LocalDate.of(2000, 1, 1), LocalDate.now());
    }

    @Operation(summary = "Programar capacitación")
    @PostMapping("/Training")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SST)
    public CapacitacionResponse addTraining(@Valid @RequestBody CapacitacionRequest req) {
        return service.programar(req);
    }

    @GetMapping("/Training")
    @PreAuthorize(LECTURA)
    public List<CapacitacionResponse> trainings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate d = desde == null ? LocalDate.now().minusMonths(6) : desde;
        LocalDate h = hasta == null ? LocalDate.now().plusMonths(6) : hasta;
        return service.capacitaciones(d, h);
    }

    @GetMapping("/Training/{id}")
    @PreAuthorize(LECTURA)
    public CapacitacionResponse training(@PathVariable Long id) {
        return service.obtenerCapacitacion(id);
    }

    @PutMapping("/Training/{id}")
    @PreAuthorize(SST)
    public CapacitacionResponse updateTraining(@PathVariable Long id, @Valid @RequestBody CapacitacionRequest req) {
        return service.editar(id, req);
    }

    @PutMapping("/Training/{id}/Status")
    @PreAuthorize(SST)
    public CapacitacionResponse trainingStatus(@PathVariable Long id,
            @Valid @RequestBody EstadoCapacitacionRequest req) {
        return service.cambiarEstado(id, req.estado());
    }

    @PutMapping("/Training/{id}/Attendance")
    @PreAuthorize(SST)
    public CapacitacionResponse attendance(@PathVariable Long id, @Valid @RequestBody AsistenciaRequest req) {
        return service.asistencia(id, req.asistentes());
    }

    @GetMapping("/Training/Employee/{idEmpleado}")
    public List<CapacitacionResponse> trainingsByEmployee(@PathVariable Long idEmpleado) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.SGSST, Roles.GERENTE, Roles.RRHH);
        return service.capacitacionesEmpleado(idEmpleado);
    }

    @Operation(summary = "Matriz de riesgos")
    @GetMapping("/Risks")
    @PreAuthorize(LECTURA)
    public List<Riesgo> risks() {
        return service.riesgos();
    }

    @PostMapping("/Risks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SST)
    public Riesgo addRisk(@Valid @RequestBody RiesgoRequest req) {
        return service.guardarRiesgo(null, req);
    }

    @PutMapping("/Risks/{id}")
    @PreAuthorize(SST)
    public Riesgo updateRisk(@PathVariable Long id, @Valid @RequestBody RiesgoRequest req) {
        return service.guardarRiesgo(id, req);
    }

    @DeleteMapping("/Risks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SST)
    public void deleteRisk(@PathVariable Long id) {
        service.eliminarRiesgo(id);
    }

    @GetMapping("/Sst/Summary")
    @PreAuthorize(LECTURA)
    public ResumenSst summary() {
        return service.resumen();
    }
}
