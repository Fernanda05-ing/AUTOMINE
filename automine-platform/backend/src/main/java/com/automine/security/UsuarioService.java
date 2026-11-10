package com.automine.security;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Roles;
import com.automine.security.UsuarioDtos.CreateUserRequest;
import com.automine.security.UsuarioDtos.EditUserRequest;
import com.automine.security.UsuarioDtos.UsuarioResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final com.automine.notificaciones.NotificacionService notificaciones;

    /** Servicio CreateUser (solo administrador). RF003: asignacion de rol. */
    @Transactional
    public UsuarioResponse crear(CreateUserRequest req) {
        if (usuarios.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Ya existe un usuario con ese correo");
        }
        Rol rol = rol(req.rol());
        if (Roles.EMPLEADO.equals(rol.getCodigo()) && req.empleadoId() == null) {
            throw ApiException.badRequest("Un usuario con rol Empleado debe estar asociado a un empleado");
        }
        Usuario u = new Usuario();
        u.setNombre(req.nombre().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRol(rol);
        u.setEmpleadoId(req.empleadoId());
        u.setTelefono(com.automine.empleados.EmpleadoService.celular(req.telefono()));
        u.setEstado(Usuario.ACTIVO);
        // La contraseña la eligió el administrador: el usuario debe cambiarla al entrar.
        u.setDebeCambiarPassword(true);
        usuarios.save(u);
        notificaciones.bienvenida(u.getEmail(), u.getNombre(), req.password());
        audit.registrar("CREAR", "Usuario", u.getId(), u.getEmail() + " rol " + rol.getCodigo());
        return UsuarioResponse.of(u);
    }

    public List<UsuarioResponse> listar() {
        return usuarios.findAllByOrderByNombreAsc().stream().map(UsuarioResponse::of).toList();
    }

    public List<Rol> roles() {
        return roles.findAll();
    }

    /** Servicio SeeProfile: el propio usuario o el administrador. */
    public UsuarioResponse perfil(Long id) {
        UsuarioActual actual = UsuarioActual.get();
        if (!actual.id().equals(id) && !actual.tieneRol(Roles.ADMIN)) {
            throw ApiException.forbidden("Solo puede consultar su propio perfil");
        }
        return UsuarioResponse.of(buscar(id));
    }

    /** Servicio EditProfile. */
    @Transactional
    public UsuarioResponse editar(EditUserRequest req) {
        UsuarioActual actual = UsuarioActual.get();
        boolean esAdmin = actual.tieneRol(Roles.ADMIN);
        boolean esPropio = actual.id().equals(req.usuarioId());
        if (!esPropio && !esAdmin) {
            throw ApiException.forbidden("Solo puede editar su propio perfil");
        }
        Usuario u = buscar(req.usuarioId());
        String email = req.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && usuarios.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe un usuario con ese correo");
        }
        u.setNombre(req.nombre().trim());
        u.setEmail(email);
        if (req.telefono() != null) {
            u.setTelefono(com.automine.empleados.EmpleadoService.celular(req.telefono()));
        }
        if (req.password() != null && !req.password().isBlank()) {
            if (esPropio && (req.passwordActual() == null
                    || !encoder.matches(req.passwordActual(), u.getPasswordHash()))) {
                throw ApiException.badRequest("La contraseña actual no es correcta");
            }
            u.setPasswordHash(encoder.encode(req.password()));
            // Si el administrador restablece la contraseña de otro, esa persona debe cambiarla al entrar.
            u.setDebeCambiarPassword(!esPropio);
        }
        if (esAdmin) {
            if (req.rol() != null && !req.rol().isBlank()) {
                if (esPropio && !Roles.ADMIN.equals(req.rol())) {
                    throw ApiException.badRequest("No puede quitarse a si mismo el rol de administrador");
                }
                u.setRol(rol(req.rol()));
            }
            if (req.empleadoId() != null) {
                u.setEmpleadoId(req.empleadoId());
            }
            if (req.estado() != null && !req.estado().isBlank()) {
                if (esPropio && !Usuario.ACTIVO.equals(req.estado())) {
                    throw ApiException.badRequest("No puede inactivar su propio usuario");
                }
                u.setEstado(req.estado());
            }
        }
        audit.registrar("EDITAR", "Usuario", u.getId(), u.getEmail());
        return UsuarioResponse.of(u);
    }

    /** Cambio de contraseña del usuario autenticado. Libera el bloqueo de contraseña temporal. */
    @Transactional
    public UsuarioResponse cambiarPassword(String actual, String nueva) {
        Usuario u = buscar(UsuarioActual.get().id());
        if (!encoder.matches(actual, u.getPasswordHash())) {
            throw ApiException.badRequest("La contraseña actual no es correcta");
        }
        if (encoder.matches(nueva, u.getPasswordHash())) {
            throw ApiException.badRequest("La nueva contraseña debe ser diferente a la actual");
        }
        u.setPasswordHash(encoder.encode(nueva));
        u.setDebeCambiarPassword(false);
        audit.registrar("CAMBIO_PASSWORD", "Usuario", u.getId(), u.getEmail());
        return UsuarioResponse.of(u);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, String estado) {
        if (UsuarioActual.get().id().equals(id)) {
            throw ApiException.badRequest("No puede cambiar el estado de su propio usuario");
        }
        Usuario u = buscar(id);
        u.setEstado(estado);
        audit.registrar("ESTADO", "Usuario", id, estado);
        return UsuarioResponse.of(u);
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
    }

    private Rol rol(String codigo) {
        return roles.findByCodigo(codigo.trim().toUpperCase())
                .orElseThrow(() -> ApiException.badRequest("Rol no válido: " + codigo));
    }
}
