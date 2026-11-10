package com.automine.chat;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.automine.chat.ChatService.Contacto;
import com.automine.chat.ChatService.Conversacion;
import com.automine.chat.ChatService.EnviarRequest;
import com.automine.chat.ChatService.MensajeDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/AutomineApi/Chat")
@RequiredArgsConstructor
@Tag(name = "11. Chat interno")
public class ChatController {

    private final ChatService chat;

    @Operation(summary = "Personas con las que puede conversar (con su celular)")
    @GetMapping("/Contacts")
    public List<Contacto> contacts() {
        return chat.contactos();
    }

    @Operation(summary = "Conversaciones recientes con mensajes sin leer")
    @GetMapping("/Conversations")
    public List<Conversacion> conversations() {
        return chat.conversaciones();
    }

    @Operation(summary = "Mensajes con un usuario (marca como leídos los recibidos)")
    @GetMapping("/Messages/{usuarioId}")
    public List<MensajeDto> messages(@PathVariable Long usuarioId, @RequestParam(required = false) Long antes,
            @RequestParam(required = false) Long despues) {
        return chat.mensajes(usuarioId, antes, despues);
    }

    @Operation(summary = "Enviar mensaje")
    @PostMapping("/Messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeDto send(@Valid @RequestBody EnviarRequest req) {
        return chat.enviar(req);
    }

    @Operation(summary = "Cantidad de mensajes sin leer")
    @GetMapping("/Unread")
    public Map<String, Long> unread() {
        return Map.of("noLeidos", chat.noLeidos());
    }
}
