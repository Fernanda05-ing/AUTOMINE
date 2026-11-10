package com.automine.push;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.common.ApiException;
import com.automine.security.UsuarioActual;

import lombok.RequiredArgsConstructor;

/** Registro del celular para notificaciones push. */
@RestController
@RequestMapping("/AutomineApi")
@RequiredArgsConstructor
public class DispositivoController {

    private final PushService push;

    @PostMapping("/Devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank() || token.length() > 400) {
            throw ApiException.badRequest("Token de dispositivo inválido");
        }
        push.registrar(UsuarioActual.get().id(), token.trim(), body.get("plataforma"));
    }

    @PostMapping("/Devices/Remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitar(@RequestBody Map<String, String> body) {
        if (body.get("token") != null) {
            push.quitar(body.get("token").trim());
        }
    }

    @GetMapping("/Push/Status")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> estado() {
        return Map.of("activo", push.activo());
    }
}
