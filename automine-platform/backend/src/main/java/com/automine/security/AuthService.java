package com.automine.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.config.AppProperties;
import com.automine.security.UsuarioDtos.LoginRequest;
import com.automine.security.UsuarioDtos.LoginResponse;
import com.automine.security.UsuarioDtos.UsuarioResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final BitacoraAcceso.Repo bitacora;
    private final TokenRevocado.Repo revocados;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final com.automine.notificaciones.NotificacionService notificaciones;
    private final AppProperties props;

    /** RF001: inicio de sesion. Bloquea temporalmente tras 5 intentos fallidos. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest req, HttpServletRequest http) {
        String email = req.email().trim().toLowerCase();
        long fallidos = bitacora.countByEmailIgnoreCaseAndEventoAndFechaAfter(email, BitacoraAcceso.LOGIN_FALLIDO,
                LocalDateTime.now().minusMinutes(MINUTOS_BLOQUEO));
        if (fallidos >= MAX_INTENTOS) {
            registrar(null, email, BitacoraAcceso.LOGIN_BLOQUEADO, http);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Intente de nuevo en " + MINUTOS_BLOQUEO + " minutos");
        }
        Usuario u = usuarios.findByEmailIgnoreCase(email).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            registrar(u == null ? null : u.getId(), email, BitacoraAcceso.LOGIN_FALLIDO, http);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        if (!Usuario.ACTIVO.equals(u.getEstado())) {
            registrar(u.getId(), email, BitacoraAcceso.LOGIN_FALLIDO, http);
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario está inactivo. Contacte al administrador");
        }
        u.setUltimoAcceso(LocalDateTime.now());
        registrar(u.getId(), email, BitacoraAcceso.LOGIN_OK, http);
        JwtService.Token token = jwt.generate(u);
        return new LoginResponse(token.value(), token.expiresAt(), UsuarioResponse.of(u));
    }

    /** RF004: cierre de sesion seguro (el token queda revocado). */
    @Transactional
    public void logout(HttpServletRequest http) {
        Object jti = http.getAttribute(JwtAuthFilter.ATTR_JTI);
        Object exp = http.getAttribute(JwtAuthFilter.ATTR_EXP);
        if (jti != null && exp instanceof Date d) {
            revocados.save(new TokenRevocado(jti.toString(),
                    LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault())));
        }
        UsuarioActual u = UsuarioActual.get();
        registrar(u.id(), u.email(), BitacoraAcceso.LOGOUT, http);
    }

    /** RF002: recuperacion de contrasena por correo. La respuesta no revela si el correo existe. */
    @Transactional
    public void forgotPassword(String email, HttpServletRequest http) {
        usuarios.findByEmailIgnoreCase(email.trim()).filter(u -> Usuario.ACTIVO.equals(u.getEstado()))
                .ifPresent(u -> {
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    u.setResetToken(sha256(token));
                    u.setResetExpira(LocalDateTime.now().plusMinutes(30));
                    registrar(u.getId(), u.getEmail(), BitacoraAcceso.RECUPERACION, http);
                    String link = props.publicUrl() + "/restablecer?token=" + token;
                    notificaciones.recuperacion(u.getEmail(), u.getNombre(), link);
                });
    }

    @Transactional
    public void resetPassword(String token, String password, HttpServletRequest http) {
        Usuario u = usuarios.findByResetToken(sha256(token))
                .filter(x -> x.getResetExpira() != null && x.getResetExpira().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> ApiException.badRequest("El enlace de recuperación no es válido o ya venció"));
        u.setPasswordHash(encoder.encode(password));
        u.setDebeCambiarPassword(false);
        u.setResetToken(null);
        u.setResetExpira(null);
        registrar(u.getId(), u.getEmail(), BitacoraAcceso.CAMBIO_PASSWORD, http);
    }

    void registrar(Long usuarioId, String email, String evento, HttpServletRequest http) {
        BitacoraAcceso b = new BitacoraAcceso();
        b.setUsuarioId(usuarioId);
        b.setEmail(email);
        b.setEvento(evento);
        if (http != null) {
            String fwd = http.getHeader("X-Forwarded-For");
            b.setIp(fwd != null ? fwd.split(",")[0].trim() : http.getRemoteAddr());
            String agent = http.getHeader("User-Agent");
            b.setAgente(agent != null && agent.length() > 255 ? agent.substring(0, 255) : agent);
        }
        b.setFecha(LocalDateTime.now());
        bitacora.save(b);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgarTokens() {
        revocados.purgar(LocalDateTime.now());
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
