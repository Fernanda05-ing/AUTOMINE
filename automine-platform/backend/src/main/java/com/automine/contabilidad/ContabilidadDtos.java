package com.automine.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ContabilidadDtos {

    private ContabilidadDtos() {
    }

    /** Servicios AddIncome / AddExpense: valor, concepto, fecha. */
    public record MovimientoRequest(
            @NotNull @DecimalMin(value = "1", message = "El valor debe ser mayor que cero") BigDecimal valor,
            @NotBlank @Size(max = 200) String concepto,
            @NotNull LocalDate fecha,
            @Size(max = 60) String categoria,
            @Size(max = 60) String referencia) {
    }

    public record CajaRequest(
            @NotBlank @Pattern(regexp = "REPOSICION|GASTO") String tipo,
            @NotNull LocalDate fecha,
            @NotBlank @Size(max = 200) String concepto,
            @NotNull @DecimalMin("1") BigDecimal valor,
            @Size(max = 60) String soporte) {
    }

    public record CajaResumen(BigDecimal saldo, BigDecimal tope, List<CajaMenor> movimientos) {
    }

    public record Linea(String concepto, BigDecimal valor) {
    }

    /** RF044: balance general simplificado a una fecha de corte. */
    public record BalanceGeneral(LocalDate fechaCorte, List<Linea> activos, BigDecimal totalActivos,
            List<Linea> pasivos, BigDecimal totalPasivos, List<Linea> patrimonio, BigDecimal totalPatrimonio) {
    }

    /** RF045: estado de resultados de un rango de fechas. */
    public record EstadoResultados(LocalDate desde, LocalDate hasta, List<Linea> ingresos, BigDecimal totalIngresos,
            List<Linea> gastos, BigDecimal totalGastos, BigDecimal utilidad, BigDecimal margen) {
    }

    public record TendenciaMes(int anio, int mes, BigDecimal ingresos, BigDecimal gastos, BigDecimal utilidad) {
    }
}
