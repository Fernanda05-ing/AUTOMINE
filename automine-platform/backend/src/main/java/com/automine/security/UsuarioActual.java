package com.automine.security;

import java.security.Principal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.automine.common.ApiException;
import com.automine.common.Roles;

/** Usuario autenticado de la peticion en curso. */
public record UsuarioActual(Long id, String email, String nombre, String rol, Long empleadoId) implements Principal {

    @Override
    public String getName() {
        return email;
    }

    public boolean tieneRol(String... roles) {
        for (String r : roles) {
            if (r.equals(rol)) {
                return true;
            }
        }
        return false;
    }

    public static UsuarioActual get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioActual u) {
            return u;
        }
        throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Sesión no valida");
    }

    /**
     * Control de alcance: los roles indicados ven a cualquier empleado; el resto
     * (por ejemplo EMPLEADO) solo puede consultar su propia informacion.
     */
    public static void verificarEmpleado(Long empleadoId, String... rolesConAccesoTotal) {
        UsuarioActual u = get();
        if (u.tieneRol(Roles.ADMIN) || u.tieneRol(rolesConAccesoTotal)) {
            return;
        }
        if (u.empleadoId() == null || !u.empleadoId().equals(empleadoId)) {
            throw ApiException.forbidden("Solo puede consultar su propia información");
        }
    }
}
