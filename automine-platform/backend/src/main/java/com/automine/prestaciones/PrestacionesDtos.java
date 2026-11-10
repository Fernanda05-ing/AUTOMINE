package com.automine.prestaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PrestacionesDtos {

    private PrestacionesDtos() {
    }

    // ------------------------------ Vacaciones ------------------------------

    public record VacacionRequest(Long empleadoId, @NotNull LocalDate fechaInicio,
            @NotNull @Min(1) @Max(60) Integer diasHabiles, @Size(max = 500) String observaciones) {
    }

    public record RevisionRequest(@NotNull Boolean aprobar, @Size(max = 500) String motivo) {
    }

    /** Vista previa: fechas calculadas segun los dias habiles y festivos. */
    public record SimulacionVacaciones(LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaRegreso,
            int diasHabiles, int diasCalendario) {
    }

    public record SaldoVacaciones(Long empleadoId, String empleado, LocalDate fechaIngreso, int diasLaborados,
            BigDecimal diasCausados, long diasDisfrutados, long diasSolicitados, BigDecimal diasDisponibles,
            boolean aplica) {
    }

    public record VacacionResponse(Long id, Long empleadoId, String cedula, String empleado, String cargo,
            String telefono, LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaRegreso, int diasHabiles,
            String observaciones, String estado, String motivoRechazo, String solicitadoPor, String revisadoPor,
            LocalDateTime fechaRevision, LocalDateTime createdAt, String aviso) {

        public static VacacionResponse of(Vacacion v, String aviso) {
            var e = v.getEmpleado();
            return new VacacionResponse(v.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(),
                    e.getTelefono(), v.getFechaInicio(), v.getFechaFin(), v.getFechaRegreso(), v.getDiasHabiles(),
                    v.getObservaciones(), v.getEstado(), v.getMotivoRechazo(), v.getSolicitadoPor(),
                    v.getRevisadoPor(), v.getFechaRevision(), v.getCreatedAt(), aviso);
        }

        public static VacacionResponse of(Vacacion v) {
            return of(v, null);
        }
    }

    // ------------------------------ Prima / cesantias ------------------------------

    /** Calculo por trabajador; {@code pagado} indica si ya se liquido en ese periodo. */
    public record PrestacionCalculo(Long empleadoId, String cedula, String empleado, String cargo,
            LocalDate desde, LocalDate hasta, BigDecimal base, int dias, BigDecimal valor, BigDecimal intereses,
            boolean pagado) {
    }

    public record PrestacionPeriodo(String tipo, int anio, int periodo, LocalDate desde, LocalDate hasta,
            String fechaLimite, BigDecimal total, BigDecimal totalIntereses, int pendientes,
            java.util.List<PrestacionCalculo> detalle) {
    }

    public record LiquidarPrestacionRequest(
            @NotNull @Pattern(regexp = "PRIMA|CESANTIAS") String tipo,
            @NotNull @Min(2020) @Max(2100) Integer anio,
            @Min(0) @Max(2) Integer periodo) {
    }

    public record PagoPrestacionResponse(Long id, Long empleadoId, String empleado, String tipo, int anio, int periodo,
            LocalDate fechaDesde, LocalDate fechaHasta, BigDecimal base, int dias, BigDecimal valor,
            String liquidadoPor, LocalDateTime createdAt) {

        public static PagoPrestacionResponse of(PagoPrestacion p) {
            return new PagoPrestacionResponse(p.getId(), p.getEmpleado().getId(), p.getEmpleado().getNombreCompleto(),
                    p.getTipo(), p.getAnio(), p.getPeriodo(), p.getFechaDesde(), p.getFechaHasta(), p.getBase(),
                    p.getDias(), p.getValor(), p.getLiquidadoPor(), p.getCreatedAt());
        }
    }

    // ------------------------------ Liquidacion de retiro ------------------------------

    public record LiquidacionRequest(@NotNull LocalDate fechaRetiro,
            @NotNull @Pattern(regexp = "RENUNCIA|SIN_JUSTA_CAUSA|JUSTA_CAUSA|MUTUO_ACUERDO|FIN_CONTRATO|PENSION") String motivo,
            LocalDate fechaFinContrato, @Size(max = 500) String observaciones) {
    }

    public record LiquidacionResponse(Long id, Long empleadoId, String cedula, String empleado, String cargo,
            String tipoContrato, LocalDate fechaIngreso, LocalDate fechaRetiro, String motivo, String observaciones,
            BigDecimal salarioBase, BigDecimal basePrestaciones, int diasSalario, BigDecimal salarioPendiente,
            int diasPrima, BigDecimal prima, int diasCesantias, BigDecimal cesantias, BigDecimal interesesCesantias,
            BigDecimal diasVacaciones, BigDecimal vacaciones, BigDecimal indemnizacion, BigDecimal total,
            String liquidadoPor, LocalDateTime createdAt) {

        public static LiquidacionResponse of(LiquidacionRetiro l) {
            var e = l.getEmpleado();
            return new LiquidacionResponse(l.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(),
                    e.getTipoContrato(), e.getFechaIngreso(), l.getFechaRetiro(), l.getMotivo(), l.getObservaciones(),
                    l.getSalarioBase(), l.getBasePrestaciones(), l.getDiasSalario(), l.getSalarioPendiente(),
                    l.getDiasPrima(), l.getPrima(), l.getDiasCesantias(), l.getCesantias(),
                    l.getInteresesCesantias(), l.getDiasVacaciones(), l.getVacaciones(), l.getIndemnizacion(),
                    l.getTotal(), l.getLiquidadoPor(), l.getCreatedAt());
        }
    }
}
