package com.automine.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.security.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Notificaciones push a la app movil con Firebase Cloud Messaging (API HTTP v1).
 * Queda desactivado hasta configurar FCM_CREDENTIALS con la ruta del JSON de la cuenta de
 * servicio de Firebase (Configuracion del proyecto > Cuentas de servicio > Generar clave).
 * El envio es asincrono: si falla no afecta la operacion que lo origino.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static volatile boolean suspendido = false;

    private final Dispositivo.Repo dispositivos;
    private final UsuarioRepository usuarios;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "push");
        t.setDaemon(true);
        return t;
    });

    private JsonNode cuenta;
    private PrivateKey llave;
    private String accessToken;
    private Instant vence = Instant.EPOCH;

    public PushService(Dispositivo.Repo dispositivos, UsuarioRepository usuarios, ObjectMapper json,
            @Value("${app.push.credentials:}") String rutaCredenciales) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
        this.json = json;
        if (rutaCredenciales != null && !rutaCredenciales.isBlank()) {
            try {
                cuenta = json.readTree(Files.readString(Path.of(rutaCredenciales)));
                String pem = cuenta.get("private_key").asText().replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                llave = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
                log.info("Notificaciones push activas (proyecto Firebase {})", cuenta.get("project_id").asText());
            } catch (Exception e) {
                cuenta = null;
                log.warn("No se pudo leer FCM_CREDENTIALS ({}): notificaciones push desactivadas", e.getMessage());
            }
        }
    }

    public boolean activo() {
        return cuenta != null && llave != null;
    }

    /** Mientras se cargan datos de demostracion no se envian notificaciones. */
    public static void suspender(boolean valor) {
        suspendido = valor;
    }

    @Transactional
    public void registrar(Long usuarioId, String token, String plataforma) {
        Dispositivo d = dispositivos.findByToken(token).orElseGet(Dispositivo::new);
        d.setUsuarioId(usuarioId);
        d.setToken(token);
        d.setPlataforma(plataforma == null || plataforma.isBlank() ? "ANDROID" : plataforma.toUpperCase());
        dispositivos.save(d);
    }

    @Transactional
    public void quitar(String token) {
        dispositivos.borrarToken(token);
    }

    /** Envia a todos los celulares del usuario (no hace nada si push no esta configurado). */
    public void aUsuario(Long usuarioId, String titulo, String cuerpo, String ruta) {
        if (!activo() || suspendido || usuarioId == null) {
            return;
        }
        List<String> tokens = dispositivos.findByUsuarioId(usuarioId).stream().map(Dispositivo::getToken).toList();
        tokens.forEach(t -> hilo.submit(() -> enviar(t, titulo, cuerpo, ruta)));
    }

    public void aEmpleado(Long empleadoId, String titulo, String cuerpo, String ruta) {
        if (!activo() || suspendido || empleadoId == null) {
            return;
        }
        usuarios.findFirstByEmpleadoId(empleadoId).ifPresent(u -> aUsuario(u.getId(), titulo, cuerpo, ruta));
    }

    private void enviar(String token, String titulo, String cuerpo, String ruta) {
        try {
            Map<String, Object> mensaje = Map.of("message", Map.of(
                    "token", token,
                    "notification", Map.of("title", titulo, "body", cuerpo.length() > 180 ? cuerpo.substring(0, 177) + "..." : cuerpo),
                    "data", Map.of("ruta", ruta == null ? "" : ruta),
                    "android", Map.of("priority", "high", "notification", Map.of("channel_id", "automine"))));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"
                    + cuenta.get("project_id").asText() + "/messages:send"))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mensaje), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404 || (res.statusCode() == 400 && res.body().contains("UNREGISTERED"))) {
                dispositivos.borrarToken(token); // la app se desinstalo o el token cambio
            } else if (res.statusCode() >= 300) {
                log.warn("FCM respondio {}: {}", res.statusCode(), res.body());
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar la notificacion push: {}", e.getMessage());
        }
    }

    /** Token OAuth2 de la cuenta de servicio (JWT firmado RS256), renovado cada hora. */
    private synchronized String token() throws Exception {
        if (accessToken != null && Instant.now().isBefore(vence.minusSeconds(60))) {
            return accessToken;
        }
        String uri = cuenta.has("token_uri") ? cuenta.get("token_uri").asText() : "https://oauth2.googleapis.com/token";
        Instant ahora = Instant.now();
        String jwt = Jwts.builder()
                .issuer(cuenta.get("client_email").asText())
                .audience().add(uri).and()
                .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(3600)))
                .signWith(llave, Jwts.SIG.RS256)
                .compact();
        HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt))
                .build();
        JsonNode r = json.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        accessToken = r.get("access_token").asText();
        vence = ahora.plusSeconds(r.get("expires_in").asLong(3600));
        return accessToken;
    }

    /** Celular registrado de un usuario. */
    @Entity(name = "Dispositivo")
    @Table(name = "dispositivos")
    @Getter
    @Setter
    public static class Dispositivo {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private Long usuarioId;
        private String token;
        private String plataforma;
        private LocalDateTime createdAt;

        @PrePersist
        void prePersist() {
            createdAt = LocalDateTime.now();
        }

        public interface Repo extends JpaRepository<Dispositivo, Long> {

            Optional<Dispositivo> findByToken(String token);

            List<Dispositivo> findByUsuarioId(Long usuarioId);

            @Modifying
            @Transactional
            @Query("delete from Dispositivo d where d.token = :token")
            int borrarToken(String token);
        }
    }
}
