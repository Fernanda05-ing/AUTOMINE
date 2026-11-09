package com.automine.nomina;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.Files;
import com.automine.common.Roles;
import com.automine.nomina.NominaDtos.GenerarPeriodoRequest;
import com.automine.nomina.NominaDtos.GenerarPeriodoResponse;
import com.automine.nomina.NominaDtos.NominaRequest;
import com.automine.nomina.NominaDtos.NominaResponse;
import com.automine.nomina.NominaDtos.NovedadesRequest;
import com.automine.nomina.NominaDtos.PeriodoResponse;
import com.automine.security.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "3. Nómina")
public class NominaController {

    private final NominaService service;

    @Operation(summary = "GeneratePayroll - liquidar nómina de un empleado")
    @PostMapping("/Payroll")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public NominaResponse generate(@Valid @RequestBody NominaRequest req) {
        return service.liquidar(req);
    }

    @Operation(summary = "Generar nómina del mes para todos los empleados activos")
    @PostMapping("/Payroll/Generate")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public GenerarPeriodoResponse generatePeriod(@Valid @RequestBody GenerarPeriodoRequest req) {
        return service.generarPeriodo(req.anio(), req.mes());
    }

    @Operation(summary = "ListPayroll - historial de nómina de un empleado")
    @GetMapping("/Payroll/{idEmpleado}")
    public List<NominaResponse> byEmployee(@PathVariable Long idEmpleado) {
        UsuarioActual.verificarEmpleado(idEmpleado, Roles.CONTADOR, Roles.RRHH, Roles.GERENTE);
        return service.historial(idEmpleado);
    }

    @GetMapping("/Payroll/Periods")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR','GERENTE','RRHH')")
    public List<PeriodoResponse> periods() {
        return service.periodos();
    }

    @GetMapping("/Payroll/Period/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR','GERENTE','RRHH')")
    public PeriodoResponse period(@PathVariable Long id) {
        return service.periodo(id);
    }

    @GetMapping("/Payroll/Period/{id}/Detail")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR','GERENTE','RRHH')")
    public List<NominaResponse> periodDetail(@PathVariable Long id) {
        return service.detallePeriodo(id);
    }

    @Operation(summary = "Aprobar periodo (RN005: solo Contabilidad)")
    @PutMapping("/Payroll/Period/{id}/Approve")
    @PreAuthorize("hasRole('CONTADOR')")
    public PeriodoResponse approve(@PathVariable Long id) {
        return service.aprobar(id);
    }

    @GetMapping("/Payroll/Detail/{id}")
    public NominaResponse detail(@PathVariable Long id) {
        NominaResponse n = service.obtener(id);
        UsuarioActual.verificarEmpleado(n.empleadoId(), Roles.CONTADOR, Roles.RRHH, Roles.GERENTE);
        return n;
    }

    @PutMapping("/Payroll/Detail/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public NominaResponse update(@PathVariable Long id, @Valid @RequestBody NovedadesRequest req) {
        return service.actualizar(id, req);
    }

    @DeleteMapping("/Payroll/Detail/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public void delete(@PathVariable Long id) {
        service.eliminar(id);
    }

    @Operation(summary = "Descargar desprendible de pago en PDF")
    @GetMapping("/Payroll/Detail/{id}/Payslip")
    public ResponseEntity<byte[]> payslip(@PathVariable Long id) {
        NominaResponse n = service.obtener(id);
        UsuarioActual.verificarEmpleado(n.empleadoId(), Roles.CONTADOR, Roles.RRHH, Roles.GERENTE);
        return Files.pdf(service.desprendible(id),
                "desprendible-" + n.anio() + "-" + String.format("%02d", n.mes()) + "-" + n.cedula() + ".pdf");
    }
}
