package com.automine.sgsst;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SgsstDtos {

    private SgsstDtos() {
    }

    /** Servicio AddAccident: empleado_id, fecha, descripcion, gravedad (+ tipo, lugar, incapacidad). */
    public record EventoRequest(
            @NotBlank @Pattern(regexp = "ACCIDENTE|INCIDENTE") String tipo,
            Long empleadoId,
            @NotNull @PastOrPresent LocalDateTime fecha,
            @NotBlank @Size(max = 120) String lugar,
            @NotBlank @Size(max = 1000) String descripcion,
            @NotBlank @Pattern(regexp = "LEVE|MODERADO|GRAVE|MORTAL") String gravedad,
            @Min(0) @Max(540) Integer diasIncapacidad,
            @Size(max = 500) String causas,
            @Size(max = 500) String acciones) {
    }

    public record SeguimientoRequest(
            @Size(max = 500) String causas,
            @Size(max = 500) String acciones,
            @Min(0) @Max(540) Integer diasIncapacidad,
            @Pattern(regexp = "ABIERTO|CERRADO") String estado) {
    }

    public record EventoResponse(Long id, String tipo, Long empleadoId, String cedula, String empleado,
            LocalDateTime fecha, String lugar, String descripcion, String gravedad, int diasIncapacidad, String causas,
            String acciones, String estado, String reportadoPor, String telefono) {

        public static EventoResponse of(EventoSst e) {
            var emp = e.getEmpleado();
            return new EventoResponse(e.getId(), e.getTipo(), emp == null ? null : emp.getId(),
                    emp == null ? null : emp.getCedula(), emp == null ? null : emp.getNombreCompleto(), e.getFecha(),
                    e.getLugar(), e.getDescripcion(), e.getGravedad(), e.getDiasIncapacidad(), e.getCausas(),
                    e.getAcciones(), e.getEstado(), e.getReportadoPor(), emp == null ? null : emp.getTelefono());
        }
    }

    public record EppRequest(
            @NotNull Long empleadoId,
            Long productoId,
            @Size(max = 120) String elemento,
            @NotNull @DecimalMin("1") BigDecimal cantidad,
            @NotNull @PastOrPresent LocalDate fecha,
            @Size(max = 255) String observaciones) {
    }

    public record EppResponse(Long id, Long empleadoId, String cedula, String empleado, Long productoId,
            String elemento, BigDecimal cantidad, LocalDate fecha, String observaciones, String entregadoPor) {

        public static EppResponse of(EntregaEpp e) {
            return new EppResponse(e.getId(), e.getEmpleado().getId(), e.getEmpleado().getCedula(),
                    e.getEmpleado().getNombreCompleto(), e.getProductoId(), e.getElemento(), e.getCantidad(),
                    e.getFecha(), e.getObservaciones(), e.getEntregadoPor());
        }
    }

    public record CapacitacionRequest(
            @NotBlank @Size(max = 150) String tema,
            @Size(max = 500) String descripcion,
            @NotNull LocalDateTime fecha,
            @NotNull @DecimalMin("0.5") BigDecimal duracionHoras,
            @NotBlank @Size(max = 100) String instructor,
            @Size(max = 100) String lugar,
            List<Long> empleadoIds) {
    }

    public record AsistenciaItem(@NotNull Long empleadoId, @NotNull Boolean asistio) {
    }

    public record AsistenciaRequest(@NotNull @Valid List<AsistenciaItem> asistentes) {
    }

    public record EstadoCapacitacionRequest(@NotBlank @Pattern(regexp = "PROGRAMADA|REALIZADA|CANCELADA") String estado) {
    }

    public record AsistenteResponse(Long empleadoId, String cedula, String empleado, boolean asistio) {
    }

    public record CapacitacionResponse(Long id, String tema, String descripcion, LocalDateTime fecha,
            BigDecimal duracionHoras, String instructor, String lugar, String estado, int convocados, int asistentes,
            List<AsistenteResponse> participantes) {

        public static CapacitacionResponse of(Capacitacion c) {
            List<AsistenteResponse> p = c.getAsistentes().stream()
                    .map(a -> new AsistenteResponse(a.getEmpleado().getId(), a.getEmpleado().getCedula(),
                            a.getEmpleado().getNombreCompleto(), a.getAsistio()))
                    .toList();
            int asistieron = (int) p.stream().filter(AsistenteResponse::asistio).count();
            return new CapacitacionResponse(c.getId(), c.getTema(), c.getDescripcion(), c.getFecha(),
                    c.getDuracionHoras(), c.getInstructor(), c.getLugar(), c.getEstado(), p.size(), asistieron, p);
        }
    }

    public record RiesgoRequest(
            @NotBlank @Size(max = 100) String proceso,
            @NotBlank @Size(max = 150) String actividad,
            @NotBlank @Size(max = 150) String peligro,
            @NotBlank @Pattern(regexp = "FISICO|QUIMICO|BIOLOGICO|BIOMECANICO|PSICOSOCIAL|MECANICO|ELECTRICO|LOCATIVO|PUBLICO|NATURAL") String clasificacion,
            @Size(max = 255) String efectos,
            @NotNull @Min(1) @Max(5) Integer probabilidad,
            @NotNull @Min(1) @Max(5) Integer severidad,
            @Size(max = 500) String controles,
            @Size(max = 100) String responsable) {
    }

    public record ResumenSst(long accidentesMes, long incidentesMes, long diasIncapacidadMes, long eventosAbiertos,
            long diasSinAccidentes, long riesgosAltos, long capacitacionesProximas) {
    }
}
