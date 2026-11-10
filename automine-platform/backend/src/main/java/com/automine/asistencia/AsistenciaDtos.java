package com.automine.asistencia;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AsistenciaDtos {

    private AsistenciaDtos() {
    }

    /** Plantilla para el celular de la SISO (reconocimiento sin conexion). */
    public record PlantillaRostro(Long empleadoId, String cedula, String nombre, String cargo, String plantilla,
            String versionModelo, LocalDateTime actualizado) {
    }

    public record EstadoRostro(Long empleadoId, String cedula, String nombre, String cargo, boolean registrado,
            LocalDateTime registradoEl, String registradoPor) {
    }

    public record MarcacionResponse(Long id, Long empleadoId, String cedula, String empleado, String cargo, String tipo,
            LocalDateTime fechaHora, String metodo, BigDecimal similitud, boolean tieneFoto, String observacion,
            boolean anulada, String registradoPor, String aviso) {

        public static MarcacionResponse of(Marcacion m, String aviso) {
            var e = m.getEmpleado();
            return new MarcacionResponse(m.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(),
                    m.getTipo(), m.getFechaHora(), m.getMetodo(), m.getSimilitud(), m.getFoto() != null,
                    m.getObservacion(), m.isAnulada(), m.getRegistradoPor(), aviso);
        }
    }

    public record AnularRequest(@NotBlank @Size(max = 300) String motivo) {
    }

    public record ResumenEmpleado(Long empleadoId, String cedula, String empleado, String cargo, int diasTrabajados,
            BigDecimal horas, BigDecimal extraDiurna, BigDecimal extraNocturna, BigDecimal extraDominical,
            int inconsistencias, List<CalculadoraJornada.Dia> dias) {
    }

    public record ResultadoNomina(int aplicadas, int sinNomina, int cerradas, List<String> detalle) {
    }
}
