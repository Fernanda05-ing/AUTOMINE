package com.automine.security;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    /** Regla de contrasena: 8+ caracteres, al menos una letra y un numero. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MSG = "Mínimo 8 caracteres con al menos una letra y un número";

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UsuarioResponse usuario) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank String rol,
            Long empleadoId,
            @Pattern(regexp = "^$|^[+]?(57)?[ -]?3[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}$", message = "Ingrese un celular colombiano de 10 dígitos (ej. 300 123 4567)") String telefono) {
    }

    /** Servicio EditProfile: el usuario edita su perfil; el administrador puede editar a cualquiera. */
    public record EditUserRequest(
            @NotNull Long usuarioId,
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Email @Size(max = 120) String email,
            @Pattern(regexp = "^$|" + PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            String passwordActual,
            String rol,
            Long empleadoId,
            String estado,
            @Pattern(regexp = "^$|^[+]?(57)?[ -]?3[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}$", message = "Ingrese un celular colombiano de 10 dígitos (ej. 300 123 4567)") String telefono) {
    }

    public record EstadoRequest(@NotBlank @Pattern(regexp = "ACTIVO|INACTIVO") String estado) {
    }

    public record UsuarioResponse(Long id, String nombre, String email, String rol, String rolNombre,
            Long empleadoId, String estado, LocalDateTime ultimoAcceso, LocalDateTime createdAt,
            boolean debeCambiarPassword, String telefono) {

        public static UsuarioResponse of(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol().getCodigo(),
                    u.getRol().getNombre(), u.getEmpleadoId(), u.getEstado(), u.getUltimoAcceso(), u.getCreatedAt(),
                    Boolean.TRUE.equals(u.getDebeCambiarPassword()), u.getTelefono());
        }
    }

    /** Cambio de contraseña del propio usuario (obligatorio si tiene una temporal). */
    public record ChangePasswordRequest(
            @NotBlank String passwordActual,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password) {
    }

    public record MensajeResponse(String message) {
    }
}
