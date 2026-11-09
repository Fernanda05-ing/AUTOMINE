package com.automine.notificaciones;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.ApiException;
import com.automine.common.MailService;
import com.automine.common.MailService.Correo;
import com.automine.config.AppProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi/Mail")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "1. Seguridad y acceso")
public class CorreoController {

    private final MailService mail;
    private final AppProperties props;

    public record PruebaRequest(@NotBlank @Email String email) {
    }

    @Operation(summary = "Estado de la configuración de correo")
    @GetMapping("/Status")
    public Map<String, Object> status() {
        return Map.of("configurado", mail.configurado(), "urlPublica", props.publicUrl());
    }

    @Operation(summary = "Enviar un correo de prueba para verificar la configuración SMTP")
    @PostMapping("/Test")
    public Map<String, String> test(@Valid @RequestBody PruebaRequest req) {
        if (!mail.configurado()) {
            throw ApiException.badRequest("El correo no está configurado: defina MAIL_USERNAME y MAIL_PASSWORD en el servidor");
        }
        try {
            mail.enviarAhora(req.email(), Correo.de("Correo de prueba de AUTOMINE", "¡El correo funciona!",
                    "Abrir AUTOMINE", props.publicUrl(),
                    "Este mensaje confirma que AUTOMINE puede enviar correos: recuperación de contraseña, "
                            + "bienvenida de usuarios y notificaciones."));
        } catch (Exception e) {
            throw ApiException.badRequest("No se pudo enviar: " + e.getMessage());
        }
        return Map.of("message", "Correo de prueba enviado a " + req.email());
    }
}
