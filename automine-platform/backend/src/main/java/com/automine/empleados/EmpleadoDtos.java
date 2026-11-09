package com.automine.empleados;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class EmpleadoDtos {

    private EmpleadoDtos() {
    }

    public record EmpleadoRequest(
            @NotBlank @Pattern(regexp = "\\d{5,15}", message = "La cédula debe tener entre 5 y 15 digitos") String cedula,
            @NotBlank @Size(max = 80) String nombres,
            @NotBlank @Size(max = 80) String apellidos,
            @Email @Size(max = 120) String email,
            @Size(max = 30) String telefono,
            @Size(max = 160) String direccion,
            @Past LocalDate fechaNacimiento,
            @NotBlank @Size(max = 80) String cargo,
            @Size(max = 80) String area,
            @NotNull @DecimalMin(value = "1", message = "El salario debe ser mayor que cero") BigDecimal salario,
            @NotNull LocalDate fechaIngreso,
            @NotBlank @Pattern(regexp = "INDEFINIDO|FIJO|OBRA_LABOR|APRENDIZAJE|PRESTACION_SERVICIOS") String tipoContrato,
            @Size(max = 80) String banco,
            @Pattern(regexp = "^$|AHORROS|CORRIENTE") String tipoCuenta,
            @Size(max = 40) String numeroCuenta,
            @Size(max = 80) String eps,
            @Size(max = 80) String fondoPension,
            @Size(max = 80) String arl,
            Boolean crearUsuario) {
    }

    public record EmpleadoResponse(Long id, String cedula, String nombres, String apellidos, String nombreCompleto,
            String email, String telefono, String direccion, LocalDate fechaNacimiento, String cargo, String area,
            BigDecimal salario, LocalDate fechaIngreso, LocalDate fechaRetiro, String tipoContrato, String banco,
            String tipoCuenta, String numeroCuenta, String eps, String fondoPension, String arl, String estado,
            LocalDateTime createdAt, Long usuarioId, String passwordTemporal) {

        public static EmpleadoResponse of(Empleado e, Long usuarioId, String passwordTemporal) {
            return new EmpleadoResponse(e.getId(), e.getCedula(), e.getNombres(), e.getApellidos(),
                    e.getNombreCompleto(), e.getEmail(), e.getTelefono(), e.getDireccion(), e.getFechaNacimiento(),
                    e.getCargo(), e.getArea(), e.getSalario(), e.getFechaIngreso(), e.getFechaRetiro(),
                    e.getTipoContrato(), e.getBanco(), e.getTipoCuenta(), e.getNumeroCuenta(), e.getEps(),
                    e.getFondoPension(), e.getArl(), e.getEstado(), e.getCreatedAt(), usuarioId, passwordTemporal);
        }

        public static EmpleadoResponse of(Empleado e) {
            return of(e, null, null);
        }
    }

    /** Lista liviana para selectores (nomina, turnos, SG-SST...). */
    public record EmpleadoOpcion(Long id, String cedula, String nombre, String cargo, String estado) {

        public static EmpleadoOpcion of(Empleado e) {
            return new EmpleadoOpcion(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), e.getEstado());
        }
    }

    public record InactivarRequest(
            @NotNull @PastOrPresent LocalDate fechaRetiro,
            @NotBlank @Size(max = 300) String motivo) {
    }

    public record NovedadRequest(
            @NotNull LocalDate fecha,
            @NotBlank @Pattern(regexp = "OBSERVACION|LLAMADO_ATENCION|RECONOCIMIENTO|INCAPACIDAD|VACACIONES|LICENCIA|CAPACITACION") String tipo,
            @NotBlank @Size(max = 500) String descripcion) {
    }

    public record ContratoRequest(
            @NotBlank @Pattern(regexp = "INDEFINIDO|FIJO|OBRA_LABOR|APRENDIZAJE|PRESTACION_SERVICIOS") String tipo,
            @NotBlank @Size(max = 80) String cargo,
            @NotNull @DecimalMin("1") BigDecimal salario,
            @NotNull LocalDate fechaInicio,
            LocalDate fechaFin) {
    }

    public record ResumenEmpleados(long total, long activos, long inactivos) {
    }
}
