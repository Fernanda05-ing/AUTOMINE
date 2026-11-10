package com.automine.prestaciones;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.Files;
import com.automine.prestaciones.PrestacionesDtos.LiquidacionRequest;
import com.automine.prestaciones.PrestacionesDtos.LiquidacionResponse;
import com.automine.prestaciones.PrestacionesDtos.LiquidarPrestacionRequest;
import com.automine.prestaciones.PrestacionesDtos.PagoPrestacionResponse;
import com.automine.prestaciones.PrestacionesDtos.PrestacionPeriodo;
import com.automine.prestaciones.PrestacionesDtos.RevisionRequest;
import com.automine.prestaciones.PrestacionesDtos.SaldoVacaciones;
import com.automine.prestaciones.PrestacionesDtos.SimulacionVacaciones;
import com.automine.prestaciones.PrestacionesDtos.VacacionRequest;
import com.automine.prestaciones.PrestacionesDtos.VacacionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "10. Vacaciones y prestaciones")
public class PrestacionesController {

    private static final String GESTIONAN = "hasAnyRole('ADMIN','RRHH')";
    private static final String NOMINA = "hasAnyRole('ADMIN','RRHH','CONTADOR')";
    private static final String CONSULTAN = "hasAnyRole('ADMIN','RRHH','CONTADOR','GERENTE')";

    private final VacacionService vacaciones;
    private final PrestacionService prestaciones;

    // ------------------------------ Vacaciones ------------------------------

    @Operation(summary = "Calcular fechas de vacaciones (días hábiles, domingos y festivos de Colombia)")
    @GetMapping("/Vacations/Simulate")
    public SimulacionVacaciones simular(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam int diasHabiles) {
        return vacaciones.simular(fechaInicio, Math.max(1, Math.min(60, diasHabiles)));
    }

    @GetMapping("/Vacations/Balance/{empleadoId}")
    public SaldoVacaciones saldo(@PathVariable Long empleadoId) {
        return vacaciones.saldo(empleadoId);
    }

    @PostMapping("/Vacation")
    @ResponseStatus(HttpStatus.CREATED)
    public VacacionResponse solicitar(@Valid @RequestBody VacacionRequest req) {
        return vacaciones.solicitar(req);
    }

    @GetMapping("/Vacations")
    public List<VacacionResponse> listar(@RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) String estado) {
        return vacaciones.listar(empleadoId, estado);
    }

    @GetMapping("/Vacations/Summary")
    @PreAuthorize(GESTIONAN)
    public Map<String, Long> resumen() {
        return Map.of("pendientes", vacaciones.pendientes());
    }

    @PutMapping("/Vacation/{id}/Review")
    @PreAuthorize(GESTIONAN)
    public VacacionResponse revisar(@PathVariable Long id, @Valid @RequestBody RevisionRequest req) {
        return vacaciones.revisar(id, req);
    }

    @PutMapping("/Vacation/{id}/Cancel")
    public VacacionResponse anular(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return vacaciones.anular(id, body == null ? null : body.get("motivo"));
    }

    // ------------------------------ Prima y cesantias ------------------------------

    @Operation(summary = "Calcular prima (semestre 1 o 2) o cesantías e intereses (anual) de todos los trabajadores")
    @GetMapping("/Benefits")
    @PreAuthorize(CONSULTAN)
    public PrestacionPeriodo calcular(@RequestParam String tipo, @RequestParam int anio,
            @RequestParam(required = false) Integer periodo) {
        return prestaciones.calcular(tipo, anio, periodo);
    }

    @PostMapping("/Benefits/Settle")
    @PreAuthorize(NOMINA)
    public PrestacionPeriodo liquidar(@Valid @RequestBody LiquidarPrestacionRequest req) {
        return prestaciones.liquidar(req);
    }

    @GetMapping("/Benefits/Employee/{empleadoId}")
    public List<PagoPrestacionResponse> historial(@PathVariable Long empleadoId) {
        return prestaciones.historial(empleadoId);
    }

    // ------------------------------ Liquidacion de retiro ------------------------------

    @Operation(summary = "Simular la liquidación final de un trabajador")
    @PostMapping("/Employee/{id}/Settlement/Preview")
    @PreAuthorize(GESTIONAN)
    public LiquidacionResponse simularRetiro(@PathVariable Long id, @Valid @RequestBody LiquidacionRequest req) {
        return prestaciones.simularRetiro(id, req);
    }

    @Operation(summary = "Confirmar la liquidación final: la guarda y retira al trabajador")
    @PostMapping("/Employee/{id}/Settlement")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTIONAN)
    public LiquidacionResponse confirmarRetiro(@PathVariable Long id, @Valid @RequestBody LiquidacionRequest req) {
        return prestaciones.confirmarRetiro(id, req);
    }

    @GetMapping("/Settlements")
    @PreAuthorize(CONSULTAN)
    public List<LiquidacionResponse> liquidaciones() {
        return prestaciones.liquidaciones();
    }

    @GetMapping("/Settlement/{id}/Pdf")
    @PreAuthorize(CONSULTAN)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return Files.pdf(prestaciones.pdfRetiro(id), "liquidacion-" + id + ".pdf");
    }
}
