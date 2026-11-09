package com.automine.produccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProduccionDtos {

    private ProduccionDtos() {
    }

    public static final String TURNOS = "MANANA|TARDE|NOCHE";

    public record FrenteRequest(
            @NotBlank @Size(max = 80) String nombre,
            @Size(max = 120) String ubicacion,
            @NotBlank @Size(max = 40) String mineral,
            @NotNull @DecimalMin("0") BigDecimal metaDiariaToneladas,
            Boolean activo) {
    }

    public record AsignacionRequest(@NotNull Long empleadoId, @NotBlank @Size(max = 80) String labor) {
    }

    /** Servicio RegisterProduction: fecha, turno, toneladas, observaciones (+ frente y personal). */
    public record ProduccionRequest(
            @NotNull @PastOrPresent LocalDate fecha,
            @NotBlank @Pattern(regexp = TURNOS) String turno,
            @NotNull Long frenteId,
            @NotNull @DecimalMin("0") BigDecimal toneladas,
            @DecimalMin("0") @DecimalMax("12") BigDecimal horasOperacion,
            @Size(max = 500) String observaciones,
            @Valid List<AsignacionRequest> asignaciones) {
    }

    public record AsignacionResponse(Long id, Long empleadoId, String cedula, String empleado, String labor) {
    }

    public record ProduccionResponse(Long id, LocalDate fecha, String turno, Long frenteId, String frente,
            String mineral, BigDecimal toneladas, BigDecimal horasOperacion, String observaciones,
            String registradoPor, LocalDateTime createdAt, int personal, List<AsignacionResponse> asignaciones) {
    }

    /** Turno asignado a un empleado (consulta desde la app movil). */
    public record TurnoEmpleado(Long produccionId, LocalDate fecha, String turno, String frente, String labor,
            BigDecimal toneladasTurno) {
    }

    public record LineaReporte(String frente, String turno, BigDecimal toneladas, BigDecimal horas, int personal,
            String observaciones) {
    }

    /** RF034: reporte diario. */
    public record ReporteDiario(LocalDate fecha, BigDecimal totalToneladas, BigDecimal metaDia, BigDecimal cumplimiento,
            int personal, List<LineaReporte> lineas) {
    }

    public record Valor(String etiqueta, BigDecimal valor) {
    }

    public record Cumplimiento(String frente, BigDecimal toneladas, BigDecimal meta, BigDecimal porcentaje) {
    }

    /** RF035: indicadores de rendimiento. */
    public record Indicadores(LocalDate desde, LocalDate hasta, BigDecimal totalToneladas, BigDecimal promedioDiario,
            BigDecimal toneladasPorHora, BigDecimal toneladasPorTrabajador, int diasConRegistro, int registros,
            List<Valor> porTurno, List<Cumplimiento> porFrente, List<Valor> serieDiaria) {
    }
}
