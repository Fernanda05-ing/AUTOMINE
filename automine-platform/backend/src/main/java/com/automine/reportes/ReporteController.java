package com.automine.reportes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.ApiException;
import com.automine.common.Files;
import com.automine.common.Roles;
import com.automine.reportes.DashboardService.Dashboard;
import com.automine.reportes.DashboardService.Kpi;
import com.automine.reportes.ReporteService.Reporte;
import com.automine.reportes.ReporteService.Tipo;
import com.automine.security.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "10. Reportes")
public class ReporteController {

    private final DashboardService dashboard;
    private final ReporteService reportes;

    @Operation(summary = "Dashboard - tablero gerencial")
    @GetMapping("/Dashboard")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','GERENTE','CONTADOR')")
    public Dashboard dashboard() {
        return dashboard.dashboard();
    }

    @Operation(summary = "Indicadores KPI de un mes")
    @GetMapping("/Kpis")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','GERENTE','CONTADOR')")
    public List<Kpi> kpis(@RequestParam int anio, @RequestParam int mes) {
        if (mes < 1 || mes > 12) {
            throw ApiException.badRequest("Mes no válido");
        }
        return dashboard.kpis(YearMonth.of(anio, mes), LocalDate.now());
    }

    /**
     * Exporta un reporte. tipo: empleados, nomina, inventario, produccion, contabilidad, sgsst, mensual.
     * formato: pdf o excel.
     */
    @Operation(summary = "Exportar reporte en PDF o Excel")
    @GetMapping("/Reports/{tipo}/{formato}")
    public ResponseEntity<byte[]> export(@PathVariable String tipo, @PathVariable String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long periodoId) {
        Tipo t;
        try {
            t = Tipo.valueOf(tipo.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Reporte");
        }
        verificarAcceso(t);
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        LocalDate d = desde == null ? h.withDayOfMonth(1) : desde;
        Reporte r = reportes.construir(t, d, h, periodoId);
        String nombre = "automine-" + r.archivo() + "-" + LocalDate.now();
        return switch (formato.toLowerCase(Locale.ROOT)) {
            case "pdf" -> Files.pdf(reportes.pdf(r), nombre + ".pdf");
            case "excel", "xlsx" -> Files.xlsx(reportes.excel(r), nombre + ".xlsx");
            default -> throw ApiException.badRequest("Formato no válido: use pdf o excel");
        };
    }

    private static void verificarAcceso(Tipo t) {
        UsuarioActual u = UsuarioActual.get();
        if (u.tieneRol(Roles.ADMIN, Roles.GERENTE)) {
            return;
        }
        boolean ok = switch (t) {
            case EMPLEADOS -> u.tieneRol(Roles.RRHH);
            case NOMINA, CONTABILIDAD, MENSUAL -> u.tieneRol(Roles.CONTADOR);
            case INVENTARIO -> u.tieneRol(Roles.ALMACENISTA, Roles.CONTADOR);
            case PRODUCCION -> u.tieneRol(Roles.SUPERVISOR);
            case SGSST -> u.tieneRol(Roles.SGSST);
        };
        if (!ok) {
            throw ApiException.forbidden("No tiene permisos para este reporte");
        }
    }
}
