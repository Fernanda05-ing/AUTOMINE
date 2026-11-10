package com.automine.common;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.automine.config.AppProperties;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * Correos de AUTOMINE (Gmail SMTP u otro servidor). Todos usan la misma plantilla HTML
 * con la marca. Sin credenciales configuradas el correo se escribe en el log.
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final AppProperties props;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();

    @Value("${spring.mail.username:}")
    private String username;

    /** Llave de Brevo: envia por HTTPS, para servidores que bloquean SMTP (como Render gratis). */
    @Value("${app.mail.brevo-key:}")
    private String brevoKey;

    @Value("${app.mail.brevo-url:https://api.brevo.com/v3/smtp/email}")
    private String brevoUrl;

    /** Contenido de un correo: titulo, parrafos y un boton opcional. */
    public record Correo(String asunto, String titulo, String[] parrafos, String boton, String url) {

        public static Correo de(String asunto, String titulo, String boton, String url, String... parrafos) {
            return new Correo(asunto, titulo, parrafos, boton, url);
        }
    }

    public boolean configurado() {
        return usaRelay() || usaBrevo() || (username != null && !username.isBlank());
    }

    /** Puente HTTPS (funcion en Vercel que envia por Gmail), para servidores que bloquean SMTP. */
    @Value("${app.mail.relay-url:}")
    private String relayUrl;

    @Value("${app.mail.relay-secret:}")
    private String relaySecret;

    private boolean usaRelay() {
        return relayUrl != null && !relayUrl.isBlank() && relaySecret != null && !relaySecret.isBlank();
    }

    private void enviarRelay(String to, Correo correo) throws Exception {
        String cuerpo = json.writeValueAsString(java.util.Map.of("to", to, "subject", correo.asunto(),
                "html", html(correo), "text", textoPlano(correo), "fromName", props.company().name()));
        var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(relayUrl.trim()))
                .header("x-relay-secret", relaySecret.trim()).header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(40))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8)).build();
        var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("El puente de correo respondió " + res.statusCode() + ": " + res.body());
        }
    }

    private boolean usaBrevo() {
        return brevoKey != null && !brevoKey.isBlank();
    }

    private void enviarBrevo(String to, Correo correo, String from) throws Exception {
        String cuerpo = json.writeValueAsString(java.util.Map.of(
                "sender", java.util.Map.of("name", props.company().name(), "email", from),
                "to", java.util.List.of(java.util.Map.of("email", to)),
                "subject", correo.asunto(),
                "htmlContent", html(correo),
                "textContent", textoPlano(correo)));
        var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(brevoUrl))
                .header("api-key", brevoKey.trim()).header("Content-Type", "application/json")
                .header("Accept", "application/json").timeout(java.time.Duration.ofSeconds(30))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8)).build();
        var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("Brevo respondió " + res.statusCode() + ": " + res.body());
        }
    }

    /** Envio en segundo plano: un fallo de correo nunca interrumpe la operacion del usuario. */
    @Async
    public void enviar(String to, Correo correo) {
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            enviarAhora(to, correo);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo '{}' a {}: {}", correo.asunto(), to, e.getMessage());
        }
    }

    /** Envio inmediato (lanza la excepcion). Lo usa la prueba de configuracion. */
    public void enviarAhora(String to, Correo correo) throws Exception {
        if (!configurado()) {
            log.info("[CORREO no configurado] Para: {} | {}\n{}", to, correo.asunto(), textoPlano(correo));
            return;
        }
        if (usaRelay()) {
            enviarRelay(to, correo);
            log.info("Correo enviado a {}: {}", to, correo.asunto());
            return;
        }
        if (usaBrevo()) {
            String remitente = props.mailFrom() == null || props.mailFrom().isBlank() ? username : props.mailFrom();
            enviarBrevo(to, correo, remitente);
            log.info("Correo enviado a {}: {}", to, correo.asunto());
            return;
        }
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        String from = props.mailFrom() == null || props.mailFrom().isBlank() ? username : props.mailFrom();
        h.setFrom(from, props.company().name());
        h.setTo(to);
        h.setSubject(correo.asunto());
        h.setText(textoPlano(correo), html(correo));
        sender.send(msg);
        log.info("Correo enviado a {}: {}", to, correo.asunto());
    }

    private String textoPlano(Correo c) {
        StringBuilder sb = new StringBuilder(c.titulo()).append("\n\n");
        for (String p : c.parrafos()) {
            sb.append(p).append("\n\n");
        }
        if (c.url() != null) {
            sb.append(c.boton() == null ? "" : c.boton() + ": ").append(c.url()).append("\n\n");
        }
        return sb.append(props.company().name()).toString();
    }

    private String html(Correo c) {
        String base = props.publicUrl();
        StringBuilder p = new StringBuilder();
        for (String parrafo : c.parrafos()) {
            p.append("<p style=\"margin:0 0 14px;color:#44403c;font-size:15px;line-height:1.6\">")
                    .append(HtmlUtils.htmlEscape(parrafo).replace("\n", "<br>")).append("</p>");
        }
        String boton = c.url() == null ? "" : """
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:22px 0 8px"><tr><td
                 style="background:#d97706;border-radius:10px"><a href="%s" style="display:inline-block;padding:12px 26px;
                 color:#ffffff;font-weight:700;font-size:15px;text-decoration:none">%s</a></td></tr></table>
                <p style="margin:0;color:#8a827a;font-size:12px">Si el botón no funciona, copie este enlace:<br>
                <a href="%s" style="color:#92400e;word-break:break-all">%s</a></p>"""
                .formatted(c.url(), HtmlUtils.htmlEscape(c.boton() == null ? "Abrir" : c.boton()), c.url(), c.url());
        return """
                <!doctype html><html lang="es"><body style="margin:0;background:#f5f3ef;font-family:Segoe UI,Arial,sans-serif">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f5f3ef;padding:28px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;
                 border-radius:16px;overflow:hidden;border:1px solid #e9e4dc">
                 <tr><td style="background:#1c1917;padding:18px 26px;border-bottom:4px solid #f2a900">
                  <table role="presentation" cellspacing="0" cellpadding="0"><tr>
                   <td style="background:#ffffff;border-radius:10px;padding:4px"><img src="%s/logo.png" width="40" height="40"
                    alt="AUTOMINE" style="display:block"></td>
                   <td style="padding-left:12px;color:#ffffff;font-size:18px;font-weight:800;letter-spacing:1px">AUTO<span
                    style="color:#f2a900">MINE</span><div style="color:#a8a29e;font-size:10px;font-weight:600;letter-spacing:1.5px">
                    GESTIÓN MINERA INTEGRAL</div></td></tr></table>
                 </td></tr>
                 <tr><td style="padding:28px 26px 26px">
                  <h1 style="margin:0 0 16px;font-size:21px;color:#1c1917">%s</h1>
                  %s%s
                 </td></tr>
                 <tr><td style="padding:16px 26px;background:#faf9f7;border-top:1px solid #e9e4dc;color:#8a827a;font-size:12px">
                  %s · NIT %s<br>Este es un mensaje automático, por favor no lo responda.</td></tr>
                </table></td></tr></table></body></html>"""
                .formatted(base, HtmlUtils.htmlEscape(c.titulo()), p, boton,
                        HtmlUtils.htmlEscape(props.company().name()), HtmlUtils.htmlEscape(props.company().nit()));
    }
}
