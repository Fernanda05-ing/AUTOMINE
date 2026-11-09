package com.automine.nomina;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class NominaDtos {

    private NominaDtos() {
    }

    /** Servicio GeneratePayroll: liquida la nomina de un empleado en un periodo. */
    public record NominaRequest(
            @NotNull Long empleadoId,
            @NotNull @Min(2020) @Max(2100) Integer anio,
            @NotNull @Min(1) @Max(12) Integer mes,
            @DecimalMin("1") BigDecimal salarioBase,
            @Min(0) @Max(30) Integer diasTrabajados,
            @DecimalMin("0") BigDecimal horasExtraDiurnas,
            @DecimalMin("0") BigDecimal horasExtraNocturnas,
            @DecimalMin("0") BigDecimal horasExtraDominicales,
            @DecimalMin("0") BigDecimal bonificaciones,
            @DecimalMin("0") BigDecimal otrosDescuentos,
            @Size(max = 255) String detalleDescuentos) {
    }

    public record NovedadesRequest(
            @NotNull @Min(0) @Max(30) Integer diasTrabajados,
            @DecimalMin("0") BigDecimal horasExtraDiurnas,
            @DecimalMin("0") BigDecimal horasExtraNocturnas,
            @DecimalMin("0") BigDecimal horasExtraDominicales,
            @DecimalMin("0") BigDecimal bonificaciones,
            @DecimalMin("0") BigDecimal otrosDescuentos,
            @Size(max = 255) String detalleDescuentos) {
    }

    public record GenerarPeriodoRequest(
            @NotNull @Min(2020) @Max(2100) Integer anio,
            @NotNull @Min(1) @Max(12) Integer mes) {
    }

    public record NominaResponse(Long id, Long periodoId, int anio, int mes, String periodoEstado, Long empleadoId,
            String cedula, String empleado, String cargo, BigDecimal salarioBase, int diasTrabajados,
            BigDecimal salarioDevengado, BigDecimal horasExtraDiurnas, BigDecimal horasExtraNocturnas,
            BigDecimal horasExtraDominicales, BigDecimal valorHorasExtra, BigDecimal bonificaciones,
            BigDecimal auxilioTransporte, BigDecimal totalDevengado, BigDecimal salud, BigDecimal pension,
            BigDecimal fondoSolidaridad, BigDecimal otrosDescuentos, String detalleDescuentos,
            BigDecimal totalDescuentos, BigDecimal netoPagar, String estado, LocalDateTime createdAt) {

        public static NominaResponse of(Nomina n) {
            var p = n.getPeriodo();
            var e = n.getEmpleado();
            return new NominaResponse(n.getId(), p.getId(), p.getAnio(), p.getMes(), p.getEstado(), e.getId(),
                    e.getCedula(), e.getNombreCompleto(), e.getCargo(), n.getSalarioBase(), n.getDiasTrabajados(),
                    n.getSalarioDevengado(), n.getHorasExtraDiurnas(), n.getHorasExtraNocturnas(),
                    n.getHorasExtraDominicales(), n.getValorHorasExtra(), n.getBonificaciones(),
                    n.getAuxilioTransporte(), n.getTotalDevengado(), n.getSalud(), n.getPension(),
                    n.getFondoSolidaridad(), n.getOtrosDescuentos(), n.getDetalleDescuentos(),
                    n.getTotalDescuentos(), n.getNetoPagar(), n.getEstado(), n.getCreatedAt());
        }
    }

    public record PeriodoResponse(Long id, int anio, int mes, String estado, long liquidaciones,
            BigDecimal totalDevengado, BigDecimal totalDescuentos, BigDecimal totalNeto, String aprobadoPor,
            LocalDateTime fechaAprobacion) {
    }

    public record GenerarPeriodoResponse(PeriodoResponse periodo, int creadas, int omitidas) {
    }
}
