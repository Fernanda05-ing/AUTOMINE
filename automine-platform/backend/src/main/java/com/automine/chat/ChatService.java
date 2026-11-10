package com.automine.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.Roles;
import com.automine.empleados.Empleado;
import com.automine.security.Usuario;
import com.automine.security.UsuarioActual;
import com.automine.security.UsuarioRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Chat interno. El personal puede escribirle a cualquier usuario; los empleados
 * pueden escribirle al personal pero no a otros empleados.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final Mensaje.Repo mensajes;
    private final UsuarioRepository usuarios;
    private final Empleado.Repo empleados;

    public record Contacto(Long id, String nombre, String rol, String rolNombre, String cargo, String telefono) {
    }

    public record Conversacion(Contacto contacto, String ultimoMensaje, LocalDateTime fecha, boolean ultimoEsMio,
            long noLeidos) {
    }

    public record MensajeDto(Long id, Long remitenteId, String texto, boolean leido, LocalDateTime fecha,
            boolean mio) {
    }

    public record EnviarRequest(@NotNull Long destinatarioId, @NotBlank @Size(max = 2000) String texto) {
    }

    /** Personas con las que el usuario actual puede conversar. */
    public List<Contacto> contactos() {
        UsuarioActual yo = UsuarioActual.get();
        List<Usuario> lista = usuarios.findAllByOrderByNombreAsc().stream()
                .filter(u -> !u.getId().equals(yo.id()) && Usuario.ACTIVO.equals(u.getEstado()))
                .filter(u -> puedeHablar(yo, u))
                .toList();
        return aContactos(lista);
    }

    /** Conversaciones del usuario, la mas reciente primero, con mensajes sin leer. */
    public List<Conversacion> conversaciones() {
        UsuarioActual yo = UsuarioActual.get();
        Map<Long, Mensaje> ultimo = new LinkedHashMap<>();
        for (Mensaje m : mensajes.recientes(yo.id(), PageRequest.of(0, 1000))) {
            Long otro = m.getRemitenteId().equals(yo.id()) ? m.getDestinatarioId() : m.getRemitenteId();
            ultimo.putIfAbsent(otro, m);
        }
        Map<Long, Long> noLeidos = new HashMap<>();
        for (Object[] fila : mensajes.noLeidosPorRemitente(yo.id())) {
            noLeidos.put((Long) fila[0], (Long) fila[1]);
        }
        Map<Long, Contacto> contactos = aContactos(usuarios.findAllById(ultimo.keySet())).stream()
                .collect(Collectors.toMap(Contacto::id, Function.identity()));
        List<Conversacion> out = new ArrayList<>();
        ultimo.forEach((otro, m) -> {
            Contacto c = contactos.get(otro);
            if (c != null) {
                out.add(new Conversacion(c, m.getTexto(), m.getCreatedAt(), m.getRemitenteId().equals(yo.id()),
                        noLeidos.getOrDefault(otro, 0L)));
            }
        });
        return out;
    }

    /** Ultimos 50 mensajes con un usuario (o los anteriores a `antes`). Marca como leidos los recibidos. */
    @Transactional
    public List<MensajeDto> mensajes(Long otroId, Long antes, Long despues) {
        UsuarioActual yo = UsuarioActual.get();
        Usuario otro = usuario(otroId);
        verificar(yo, otro);
        List<Mensaje> lista;
        if (despues != null) {
            lista = mensajes.nuevos(yo.id(), otroId, despues);
        } else {
            lista = new ArrayList<>(mensajes.conversacion(yo.id(), otroId, antes == null ? Long.MAX_VALUE : antes,
                    PageRequest.of(0, 50)));
            lista.sort(Comparator.comparing(Mensaje::getId));
        }
        mensajes.marcarLeidos(yo.id(), otroId);
        return lista.stream().map(m -> dto(m, yo.id())).toList();
    }

    @Transactional
    public MensajeDto enviar(EnviarRequest req) {
        UsuarioActual yo = UsuarioActual.get();
        Usuario otro = usuario(req.destinatarioId());
        if (!Usuario.ACTIVO.equals(otro.getEstado())) {
            throw ApiException.badRequest("El usuario está inactivo y no puede recibir mensajes");
        }
        verificar(yo, otro);
        Mensaje m = new Mensaje();
        m.setRemitenteId(yo.id());
        m.setDestinatarioId(otro.getId());
        m.setTexto(req.texto().trim());
        m.setLeido(false);
        mensajes.save(m);
        return dto(m, yo.id());
    }

    public long noLeidos() {
        return mensajes.countByDestinatarioIdAndLeidoFalse(UsuarioActual.get().id());
    }

    private void verificar(UsuarioActual yo, Usuario otro) {
        if (otro.getId().equals(yo.id())) {
            throw ApiException.badRequest("No puede enviarse mensajes a sí mismo");
        }
        if (!puedeHablar(yo, otro)) {
            throw ApiException.forbidden("Los empleados solo pueden escribirle al personal administrativo");
        }
    }

    private static boolean puedeHablar(UsuarioActual yo, Usuario otro) {
        return !(Roles.EMPLEADO.equals(yo.rol()) && Roles.EMPLEADO.equals(otro.getRol().getCodigo()));
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
    }

    /** El celular es el del usuario o, si no tiene, el de su ficha de empleado. */
    private List<Contacto> aContactos(List<Usuario> lista) {
        Map<Long, Empleado> fichas = empleados.findAllById(lista.stream().map(Usuario::getEmpleadoId)
                .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Empleado::getId, Function.identity()));
        return lista.stream().map(u -> {
            Empleado e = u.getEmpleadoId() == null ? null : fichas.get(u.getEmpleadoId());
            String tel = u.getTelefono() != null ? u.getTelefono() : e == null ? null : e.getTelefono();
            return new Contacto(u.getId(), u.getNombre(), u.getRol().getCodigo(), u.getRol().getNombre(),
                    e == null ? null : e.getCargo(), tel);
        }).toList();
    }

    private static MensajeDto dto(Mensaje m, Long yo) {
        return new MensajeDto(m.getId(), m.getRemitenteId(), m.getTexto(), m.getLeido(), m.getCreatedAt(),
                m.getRemitenteId().equals(yo));
    }
}
