package com.automine.sgsst;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.contabilidad.ContabilidadService;
import com.automine.empleados.Empleado;
import com.automine.empleados.HistorialLaboral;
import com.automine.inventario.InventarioService;
import com.automine.inventario.Producto;
import com.automine.sgsst.SgsstDtos.AsistenciaItem;
import com.automine.sgsst.SgsstDtos.CapacitacionRequest;
import com.automine.sgsst.SgsstDtos.CapacitacionResponse;
import com.automine.sgsst.SgsstDtos.EppRequest;
import com.automine.sgsst.SgsstDtos.EppResponse;
import com.automine.sgsst.SgsstDtos.EventoRequest;
import com.automine.sgsst.SgsstDtos.EventoResponse;
import com.automine.sgsst.SgsstDtos.ResumenSst;
import com.automine.sgsst.SgsstDtos.RiesgoRequest;
import com.automine.sgsst.SgsstDtos.SeguimientoRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SgsstService {

    private final EventoSst.Repo eventos;
    private final EntregaEpp.Repo entregas;
    private final Capacitacion.Repo capacitaciones;
    private final Riesgo.Repo riesgos;
    private final Empleado.Repo empleados;
    private final HistorialLaboral.Repo historial;
    private final InventarioService inventario;
    private final AuditService audit;
    private final com.automine.notificaciones.NotificacionService notificaciones;

    // ------------------------- Accidentes e incidentes -------------------------

    /** Servicio AddAccident (RF036) y registro de incidentes (RF037). */
    @Transactional
    public EventoResponse registrarEvento(EventoRequest req) {
        Empleado e = null;
        if (req.empleadoId() != null) {
            e = empleado(req.empleadoId());
        } else if (EventoSst.ACCIDENTE.equals(req.tipo())) {
            throw ApiException.badRequest("Un accidente debe indicar el trabajador afectado");
        }
        EventoSst ev = new EventoSst();
        ev.setTipo(req.tipo());
        ev.setEmpleado(e);
        ev.setFecha(req.fecha());
        ev.setLugar(req.lugar().trim());
        ev.setDescripcion(req.descripcion().trim());
        ev.setGravedad(req.gravedad());
        ev.setDiasIncapacidad(req.diasIncapacidad() == null ? 0 : req.diasIncapacidad());
        ev.setCausas(req.causas());
        ev.setAcciones(req.acciones());
        ev.setEstado(EventoSst.ABIERTO);
        ev.setReportadoPor(AuditService.usuarioActual());
        eventos.save(ev);
        if (e != null && EventoSst.ACCIDENTE.equals(req.tipo())) {
            HistorialLaboral h = new HistorialLaboral();
            h.setEmpleadoId(e.getId());
            h.setFecha(req.fecha().toLocalDate());
            h.setTipo("ACCIDENTE_LABORAL");
            h.setDescripcion("Accidente " + req.gravedad().toLowerCase() + " en " + req.lugar()
                    + (ev.getDiasIncapacidad() > 0 ? " con " + ev.getDiasIncapacidad() + " días de incapacidad" : ""));
            h.setRegistradoPor(AuditService.usuarioActual());
            historial.save(h);
        }
        audit.registrar("REGISTRAR_" + req.tipo(), "SGSST", ev.getId(), req.gravedad() + " " + req.lugar());
        notificaciones.accidenteReportado(req.tipo(), req.gravedad(), req.lugar(), e == null ? null : e.getNombreCompleto(),
                req.descripcion());
        return EventoResponse.of(ev);
    }

    @Transactional
    public EventoResponse seguimiento(Long id, SeguimientoRequest req) {
        EventoSst ev = eventos.findById(id).orElseThrow(() -> ApiException.notFound("Evento"));
        if (req.causas() != null) {
            ev.setCausas(req.causas());
        }
        if (req.acciones() != null) {
            ev.setAcciones(req.acciones());
        }
        if (req.diasIncapacidad() != null) {
            ev.setDiasIncapacidad(req.diasIncapacidad());
        }
        if (req.estado() != null) {
            if (EventoSst.CERRADO.equals(req.estado()) && (ev.getAcciones() == null || ev.getAcciones().isBlank())) {
                throw ApiException.badRequest("Para cerrar el caso registre las acciones correctivas");
            }
            ev.setEstado(req.estado());
        }
        audit.registrar("SEGUIMIENTO", "SGSST", id, ev.getEstado());
        return EventoResponse.of(ev);
    }

    public EventoResponse evento(Long id) {
        return EventoResponse.of(eventos.findById(id).orElseThrow(() -> ApiException.notFound("Evento")));
    }

    public List<EventoResponse> eventos(String tipo, LocalDate desde, LocalDate hasta) {
        ContabilidadService.validarRango(desde, hasta);
        return eventos.buscar(tipo == null || tipo.isBlank() ? null : tipo, desde.atStartOfDay(),
                hasta.atTime(LocalTime.MAX)).stream().map(EventoResponse::of).toList();
    }

    public List<EventoResponse> eventosEmpleado(Long empleadoId) {
        return eventos.findByEmpleadoIdOrderByFechaDesc(empleadoId).stream().map(EventoResponse::of).toList();
    }

    // ----------------------------------- EPP -----------------------------------

    /** RF038: entrega de EPP. Si el elemento viene del almacen, se descuenta del inventario. */
    @Transactional
    public EppResponse entregarEpp(EppRequest req) {
        Empleado e = empleado(req.empleadoId());
        if (!e.isActivo()) {
            throw ApiException.badRequest("No se entrega EPP a empleados inactivos");
        }
        String elemento = req.elemento();
        if (req.productoId() != null) {
            Producto p = inventario.buscar(req.productoId());
            elemento = p.getNombre();
            inventario.salida(p.getId(), req.cantidad(), "Entrega EPP a " + e.getNombreCompleto(),
                    AuditService.usuarioActual());
        }
        if (elemento == null || elemento.isBlank()) {
            throw ApiException.badRequest("Indique el elemento entregado o seleccionelo del inventario");
        }
        EntregaEpp ent = new EntregaEpp();
        ent.setEmpleado(e);
        ent.setProductoId(req.productoId());
        ent.setElemento(elemento.trim());
        ent.setCantidad(req.cantidad());
        ent.setFecha(req.fecha());
        ent.setObservaciones(req.observaciones());
        ent.setEntregadoPor(AuditService.usuarioActual());
        entregas.save(ent);
        audit.registrar("ENTREGA_EPP", "SGSST", ent.getId(), e.getNombreCompleto() + ": " + elemento);
        return EppResponse.of(ent);
    }

    public List<EppResponse> entregas(Long empleadoId, LocalDate desde, LocalDate hasta) {
        return entregas.buscar(empleadoId, desde, hasta).stream().map(EppResponse::of).toList();
    }

    // ------------------------------ Capacitaciones ------------------------------

    @Transactional
    public CapacitacionResponse programar(CapacitacionRequest req) {
        Capacitacion c = new Capacitacion();
        aplicar(c, req);
        c.setEstado(Capacitacion.PROGRAMADA);
        convocar(c, req.empleadoIds());
        capacitaciones.save(c);
        audit.registrar("PROGRAMAR", "Capacitación", c.getId(), c.getTema());
        return CapacitacionResponse.of(c);
    }

    @Transactional
    public CapacitacionResponse editar(Long id, CapacitacionRequest req) {
        Capacitacion c = capacitacion(id);
        aplicar(c, req);
        if (req.empleadoIds() != null) {
            convocar(c, req.empleadoIds());
        }
        audit.registrar("EDITAR", "Capacitación", id, c.getTema());
        return CapacitacionResponse.of(c);
    }

    @Transactional
    public CapacitacionResponse cambiarEstado(Long id, String estado) {
        Capacitacion c = capacitacion(id);
        c.setEstado(estado);
        audit.registrar("ESTADO", "Capacitación", id, estado);
        return CapacitacionResponse.of(c);
    }

    /** Registra la asistencia y marca la capacitacion como realizada. */
    @Transactional
    public CapacitacionResponse asistencia(Long id, List<AsistenciaItem> items) {
        Capacitacion c = capacitacion(id);
        if (Capacitacion.CANCELADA.equals(c.getEstado())) {
            throw ApiException.badRequest("La capacitación está cancelada");
        }
        Map<Long, Boolean> marcas = new HashMap<>();
        items.forEach(i -> marcas.put(i.empleadoId(), i.asistio()));
        Set<Long> existentes = new HashSet<>();
        for (Capacitacion.Asistente a : c.getAsistentes()) {
            existentes.add(a.getEmpleado().getId());
            if (marcas.containsKey(a.getEmpleado().getId())) {
                a.setAsistio(marcas.get(a.getEmpleado().getId()));
            }
        }
        marcas.forEach((empleadoId, asistio) -> {
            if (!existentes.contains(empleadoId)) {
                Capacitacion.Asistente a = new Capacitacion.Asistente();
                a.setCapacitacion(c);
                a.setEmpleado(empleado(empleadoId));
                a.setAsistio(asistio);
                c.getAsistentes().add(a);
            }
        });
        c.setEstado(Capacitacion.REALIZADA);
        audit.registrar("ASISTENCIA", "Capacitación", id, marcas.size() + " registros");
        return CapacitacionResponse.of(c);
    }

    public List<CapacitacionResponse> capacitaciones(LocalDate desde, LocalDate hasta) {
        return capacitaciones.buscar(desde.atStartOfDay(), hasta.atTime(LocalTime.MAX)).stream()
                .map(CapacitacionResponse::of).toList();
    }

    public List<CapacitacionResponse> capacitacionesEmpleado(Long empleadoId) {
        return capacitaciones.delEmpleado(empleadoId).stream().map(CapacitacionResponse::of).toList();
    }

    public CapacitacionResponse obtenerCapacitacion(Long id) {
        return CapacitacionResponse.of(capacitacion(id));
    }

    // ------------------------------ Matriz de riesgos ------------------------------

    public List<Riesgo> riesgos() {
        return riesgos.findAllByOrderByProcesoAscActividadAsc();
    }

    @Transactional
    public Riesgo guardarRiesgo(Long id, RiesgoRequest req) {
        Riesgo r = id == null ? new Riesgo() : riesgos.findById(id).orElseThrow(() -> ApiException.notFound("Riesgo"));
        r.setProceso(req.proceso().trim());
        r.setActividad(req.actividad().trim());
        r.setPeligro(req.peligro().trim());
        r.setClasificacion(req.clasificacion());
        r.setEfectos(req.efectos());
        r.setProbabilidad(req.probabilidad());
        r.setSeveridad(req.severidad());
        r.setControles(req.controles());
        r.setResponsable(req.responsable());
        riesgos.save(r);
        audit.registrar(id == null ? "CREAR" : "EDITAR", "Riesgo", r.getId(), r.getPeligro() + " nivel " + r.getNivel());
        return r;
    }

    @Transactional
    public void eliminarRiesgo(Long id) {
        Riesgo r = riesgos.findById(id).orElseThrow(() -> ApiException.notFound("Riesgo"));
        riesgos.delete(r);
        audit.registrar("ELIMINAR", "Riesgo", id, r.getPeligro());
    }

    // ------------------------------ Indicadores ------------------------------

    public ResumenSst resumen() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime fin = hoy.atTime(LocalTime.MAX);
        long diasSin = eventos.ultimoAccidente().stream().findFirst()
                .map(e -> ChronoUnit.DAYS.between(e.getFecha().toLocalDate(), hoy)).orElse(-1L);
        long altos = riesgos.findAll().stream().filter(r -> r.getNivel() >= 10).count();
        return new ResumenSst(eventos.countByTipoAndFechaBetween(EventoSst.ACCIDENTE, inicioMes, fin),
                eventos.countByTipoAndFechaBetween(EventoSst.INCIDENTE, inicioMes, fin),
                eventos.diasIncapacidad(inicioMes, fin), eventos.countByEstado(EventoSst.ABIERTO), diasSin, altos,
                capacitaciones.proximas(LocalDateTime.now()).size());
    }

    private Capacitacion capacitacion(Long id) {
        return capacitaciones.findById(id).orElseThrow(() -> ApiException.notFound("Capacitación"));
    }

    private Empleado empleado(Long id) {
        return empleados.findById(id).orElseThrow(() -> ApiException.notFound("Empleado"));
    }

    private void aplicar(Capacitacion c, CapacitacionRequest req) {
        c.setTema(req.tema().trim());
        c.setDescripcion(req.descripcion());
        c.setFecha(req.fecha());
        c.setDuracionHoras(req.duracionHoras());
        c.setInstructor(req.instructor().trim());
        c.setLugar(req.lugar());
    }

    private void convocar(Capacitacion c, List<Long> empleadoIds) {
        Set<Long> nuevos = empleadoIds == null ? Set.of() : new HashSet<>(empleadoIds);
        c.getAsistentes().removeIf(a -> !nuevos.contains(a.getEmpleado().getId()));
        Set<Long> existentes = new HashSet<>();
        c.getAsistentes().forEach(a -> existentes.add(a.getEmpleado().getId()));
        for (Long id : nuevos) {
            if (!existentes.contains(id)) {
                Capacitacion.Asistente a = new Capacitacion.Asistente();
                a.setCapacitacion(c);
                a.setEmpleado(empleado(id));
                a.setAsistio(false);
                c.getAsistentes().add(a);
            }
        }
    }
}
