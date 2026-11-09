package com.automine.contabilidad;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.contabilidad.ContabilidadDtos.BalanceGeneral;
import com.automine.contabilidad.ContabilidadDtos.CajaRequest;
import com.automine.contabilidad.ContabilidadDtos.CajaResumen;
import com.automine.contabilidad.ContabilidadDtos.EstadoResultados;
import com.automine.contabilidad.ContabilidadDtos.MovimientoRequest;
import com.automine.contabilidad.ContabilidadDtos.TendenciaMes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad")
@PreAuthorize("hasAnyRole('ADMIN','CONTADOR','GERENTE')")
public class ContabilidadController {

    private final ContabilidadService service;

    @Operation(summary = "AddIncome - registrar ingreso")
    @PostMapping("/Income")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public MovimientoContable addIncome(@Valid @RequestBody MovimientoRequest req) {
        return service.registrar(MovimientoContable.INGRESO, req);
    }

    @Operation(summary = "AddExpense - registrar gasto")
    @PostMapping("/Expense")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public MovimientoContable addExpense(@Valid @RequestBody MovimientoRequest req) {
        return service.registrar(MovimientoContable.GASTO, req);
    }

    @GetMapping("/Income")
    public List<MovimientoContable> incomes(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar(MovimientoContable.INGRESO, desde, hasta);
    }

    @GetMapping("/Expense")
    public List<MovimientoContable> expenses(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar(MovimientoContable.GASTO, desde, hasta);
    }

    @DeleteMapping("/Accounting/Entry/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public void deleteEntry(@PathVariable Long id) {
        service.eliminar(id);
    }

    @Operation(summary = "Caja menor: saldo y movimientos")
    @GetMapping("/PettyCash")
    public CajaResumen pettyCash(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.caja(desde, hasta);
    }

    @PostMapping("/PettyCash")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public CajaMenor addPettyCash(@Valid @RequestBody CajaRequest req) {
        return service.movimientoCaja(req);
    }

    @Operation(summary = "Balance general a una fecha de corte")
    @GetMapping("/Accounting/Balance")
    public BalanceGeneral balance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate corte) {
        return service.balance(corte == null ? LocalDate.now() : corte);
    }

    @Operation(summary = "Estado de resultados de un periodo")
    @GetMapping("/Accounting/IncomeStatement")
    public EstadoResultados incomeStatement(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.estadoResultados(desde, hasta);
    }

    @GetMapping("/Accounting/Trend")
    public List<TendenciaMes> trend(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ContabilidadService.validarRango(desde, hasta);
        return service.tendencia(desde, hasta);
    }
}
