package com.automine.incapacidades;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class IncapacidadDtos {

    private IncapacidadDtos() {
    }

    /** Datos del certificado (llegan como campos del formulario multipart junto con el soporte). */
    public record IncapacidadRequest(Long empleadoId, String tipo, LocalDate fechaInicio, LocalDate fechaFin,
            Boolean prorroga, String entidad, String numeroCertificado, String observaciones) {
    }

    public record RevisionRequest(@NotNull Boolean aprobar, @Size(max = 500) String motivo) {
    }

    public record AnulacionRequest(@NotNull @Size(min = 3, max = 500) String motivo) {
    }

    public record IncapacidadResponse(Long id, Long empleadoId, String cedula, String empleado, String cargo,
            String telefono, String tipo, LocalDate fechaInicio, LocalDate fechaFin, int dias, boolean prorroga,
            String entidad, String numeroCertificado, String observaciones, String estado, String motivoRechazo,
            String soporteNombre, String registradoPor, String revisadoPor, LocalDateTime fechaRevision,
            LocalDateTime createdAt, String aviso) {

        public static IncapacidadResponse of(Incapacidad i) {
            return of(i, null);
        }

        public static IncapacidadResponse of(Incapacidad i, String aviso) {
            var e = i.getEmpleado();
            return new IncapacidadResponse(i.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(),
                    e.getTelefono(), i.getTipo(), i.getFechaInicio(), i.getFechaFin(), i.getDias(), i.isProrroga(),
                    i.getEntidad(), i.getNumeroCertificado(), i.getObservaciones(), i.getEstado(),
                    i.getMotivoRechazo(), i.getSoporteNombre(), i.getRegistradoPor(), i.getRevisadoPor(),
                    i.getFechaRevision(), i.getCreatedAt(), aviso);
        }
    }

    public record ResumenIncapacidades(long pendientes) {
    }
}
