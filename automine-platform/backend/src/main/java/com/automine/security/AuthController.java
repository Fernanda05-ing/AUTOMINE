package com.automine.security;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.Auditoria;
import com.automine.common.AuditoriaRepository;
import com.automine.common.Parametro;
import com.automine.common.ParametroService;
import com.automine.security.UsuarioDtos.CreateUserRequest;
import com.automine.security.UsuarioDtos.EditUserRequest;
import com.automine.security.UsuarioDtos.EstadoRequest;
import com.automine.security.UsuarioDtos.ForgotPasswordRequest;
import com.automine.security.UsuarioDtos.LoginRequest;
import com.automine.security.UsuarioDtos.LoginResponse;
import com.automine.security.UsuarioDtos.MensajeResponse;
import com.automine.security.UsuarioDtos.ResetPasswordRequest;
import com.automine.security.UsuarioDtos.UsuarioResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
@Tag(name = "1. Seguridad y acceso")
public class AuthController {

    private final AuthService auth;
    private final UsuarioService usuarios;
    private final BitacoraAcceso.Repo bitacora;
    private final AuditoriaRepository auditoria;
    private final ParametroService parametros;

    @GetMapping("/Health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "AutomineApi");
    }

    @Operation(summary = "LoginUser - iniciar sesión")
    @PostMapping("/Login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http);
    }

    @Operation(summary = "Cerrar sesión (revoca el token)")
    @PostMapping("/Logout")
    public MensajeResponse logout(HttpServletRequest http) {
        auth.logout(http);
        return new MensajeResponse("Sesión cerrada");
    }

    @Operation(summary = "Solicitar enlace de recuperación de contraseña")
    @PostMapping("/ForgotPassword")
    public MensajeResponse forgot(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        auth.forgotPassword(req.email(), http);
        return new MensajeResponse("Si el correo está registrado, recibirá un enlace para restablecer la contraseña");
    }

    @Operation(summary = "Restablecer contraseña con el enlace recibido")
    @PostMapping("/ResetPassword")
    public MensajeResponse reset(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        auth.resetPassword(req.token(), req.password(), http);
        return new MensajeResponse("Contraseña actualizada. Ya puede iniciar sesión");
    }

    @Operation(summary = "CreateUser - crear usuario")
    @PostMapping("/CreateUser")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResponse create(@Valid @RequestBody CreateUserRequest req) {
        return usuarios.crear(req);
    }

    @Operation(summary = "SeeProfile - perfil del usuario autenticado")
    @GetMapping("/Profile")
    public UsuarioResponse me() {
        return usuarios.perfil(UsuarioActual.get().id());
    }

    @Operation(summary = "SeeProfile - ver perfil por id")
    @GetMapping("/Profile/{id}")
    public UsuarioResponse profile(@PathVariable Long id) {
        return usuarios.perfil(id);
    }

    @Operation(summary = "EditProfile - editar perfil")
    @PutMapping("/EditUser")
    public UsuarioResponse edit(@Valid @RequestBody EditUserRequest req) {
        return usuarios.editar(req);
    }

    @Operation(summary = "Cambiar la contraseña propia (obligatorio si es temporal)")
    @PostMapping("/ChangePassword")
    public UsuarioResponse changePassword(@Valid @RequestBody UsuarioDtos.ChangePasswordRequest req) {
        return usuarios.cambiarPassword(req.passwordActual(), req.password());
    }

    @GetMapping("/Users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResponse> users() {
        return usuarios.listar();
    }

    @PutMapping("/Users/{id}/Status")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResponse status(@PathVariable Long id, @Valid @RequestBody EstadoRequest req) {
        return usuarios.cambiarEstado(id, req.estado());
    }

    @GetMapping("/Roles")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Rol> roles() {
        return usuarios.roles();
    }

    @Operation(summary = "Bitácora de accesos")
    @GetMapping("/AccessLog")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<BitacoraAcceso> accessLog(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return bitacora.findAllByOrderByFechaDesc(PageRequest.of(page, Math.min(size, 200)));
    }

    @Operation(summary = "Auditoría de acciones")
    @GetMapping("/Audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<Auditoria> audit(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditoria.findAllByOrderByFechaDesc(PageRequest.of(page, Math.min(size, 200)));
    }

    @Operation(summary = "Parámetros del sistema (nómina, caja menor)")
    @GetMapping("/Settings")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public List<Parametro> settings() {
        return parametros.listar();
    }

    @PutMapping("/Settings/{clave}")
    @PreAuthorize("hasRole('ADMIN')")
    public Parametro updateSetting(@PathVariable String clave, @RequestBody Map<String, String> body) {
        String valor = body.get("valor");
        if (valor == null || valor.isBlank()) {
            throw com.automine.common.ApiException.badRequest("Debe indicar el valor");
        }
        return parametros.actualizar(clave, valor);
    }
}
