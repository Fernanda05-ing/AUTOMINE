package com.automine.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_JTI = "automine.jti";
    public static final String ATTR_EXP = "automine.exp";

    private static final java.util.Set<String> PERMITIDOS_CON_TEMPORAL = java.util.Set.of(
            "/AutomineApi/ChangePassword", "/AutomineApi/Profile", "/AutomineApi/Logout");

    private final JwtService jwtService;
    private final UsuarioRepository usuarios;
    private final TokenRevocado.Repo revocados;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtService.parse(header.substring(7));
            if (claims != null && !revocados.existsById(claims.getId())) {
                Usuario usuario = usuarios.findByEmailIgnoreCase(claims.getSubject())
                        .filter(u -> Usuario.ACTIVO.equals(u.getEstado())).orElse(null);
                // Con contraseña temporal solo puede cambiarla, ver su perfil o cerrar sesion.
                if (usuario != null && Boolean.TRUE.equals(usuario.getDebeCambiarPassword())
                        && !PERMITIDOS_CON_TEMPORAL.contains(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"status\":403,\"code\":\"CAMBIO_PASSWORD\","
                            + "\"message\":\"Debe cambiar su contraseña temporal antes de continuar\"}");
                    return;
                }
                java.util.Optional.ofNullable(usuario)
                        .ifPresent(u -> {
                            UsuarioActual actual = new UsuarioActual(u.getId(), u.getEmail(), u.getNombre(),
                                    u.getRol().getCodigo(), u.getEmpleadoId());
                            var auth = new UsernamePasswordAuthenticationToken(actual, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + actual.rol())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            request.setAttribute(ATTR_JTI, claims.getId());
                            request.setAttribute(ATTR_EXP, claims.getExpiration());
                        });
            }
        }
        chain.doFilter(request, response);
    }
}
