package com.automine.consignaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class ConsignacionDtos {

    private ConsignacionDtos() {
    }

    /** Servicio RegisterTransfer: nomina_id, banco, cuenta, valor, fecha_pago (+ referencia, RF019). */
    public record ConsignacionRequest(
            @NotNull Long nominaId,
            @Size(max = 80) String banco,
            @Size(max = 40) String cuenta,
            @DecimalMin("1") BigDecimal valor,
            @NotNull @PastOrPresent LocalDate fechaPago,
            @NotBlank @Size(max = 60) String referencia,
            @Size(max = 255) String observaciones) {
    }

    public record LoteRequest(@NotNull @PastOrPresent LocalDate fechaPago, @Size(max = 20) String prefijoReferencia) {
    }

    public record LoteResponse(int registradas, int sinCuenta, int yaConsignadas) {
    }

    public record AnularRequest(@NotBlank @Size(max = 255) String motivo) {
    }

    public record ConsignacionResponse(Long id, Long nominaId, Long empleadoId, String cedula, String empleado,
            int anio, int mes, BigDecimal netoNomina, String banco, String cuenta, BigDecimal valor,
            LocalDate fechaPago, String referencia, String estado, boolean tieneComprobante, String comprobanteNombre,
            String observaciones, String registradoPor, String aprobadoPor, LocalDateTime fechaAprobacion,
            String telefono) {

        public static ConsignacionResponse of(Consignacion c) {
            var n = c.getNomina();
            var e = n.getEmpleado();
            var p = n.getPeriodo();
            return new ConsignacionResponse(c.getId(), n.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(),
                    p.getAnio(), p.getMes(), n.getNetoPagar(), c.getBanco(), enmascarar(c.getCuenta()), c.getValor(),
                    c.getFechaPago(), c.getReferencia(), c.getEstado(), c.getComprobanteNombre() != null,
                    c.getComprobanteNombre(), c.getObservaciones(), c.getRegistradoPor(), c.getAprobadoPor(),
                    c.getFechaAprobacion(), e.getTelefono());
        }

        /** Solo se muestran los ultimos 4 digitos de la cuenta. */
        static String enmascarar(String cuenta) {
            if (cuenta == null || cuenta.length() <= 4) {
                return cuenta;
            }
            return "****" + cuenta.substring(cuenta.length() - 4);
        }
    }
}
