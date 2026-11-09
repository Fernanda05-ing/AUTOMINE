package com.automine.produccion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.contabilidad.ContabilidadService;
import com.automine.empleados.Empleado;
import com.automine.produccion.ProduccionDtos.AsignacionRequest;
import com.automine.produccion.ProduccionDtos.AsignacionResponse;
import com.automine.produccion.ProduccionDtos.Cumplimiento;
import com.automine.produccion.ProduccionDtos.FrenteRequest;
import com.automine.produccion.ProduccionDtos.Indicadores;
import com.automine.produccion.ProduccionDtos.LineaReporte;
import com.automine.produccion.ProduccionDtos.ProduccionRequest;
import com.automine.produccion.ProduccionDtos.ProduccionResponse;
import com.automine.produccion.ProduccionDtos.ReporteDiario;
import com.automine.produccion.ProduccionDtos.TurnoEmpleado;
import com.automine.produccion.ProduccionDtos.Valor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProduccionService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final Produccion.Repo producciones;
    private final Produccion.Asignacion.Repo asignaciones;
    private final FrenteTrabajo.Repo frentes;
    private final Empleado.Repo empleados;
    private final AuditService audit;

    // ------------------------------ Frentes ------------------------------

    public List<FrenteTrabajo> frentes() {
        return frentes.findAllByOrderByNombreAsc();
    }

    @Transactional
    public FrenteTrabajo guardarFrente(Long id, FrenteRequest req) {
        FrenteTrabajo f = id == null ? new FrenteTrabajo()
                : frentes.findById(id).orElseThrow(() -> ApiException.notFound("Frente"));
        boolean nombreCambia = f.getNombre() == null || !f.getNombre().equalsIgnoreCase(req.nombre().trim());
        if (nombreCambia && frentes.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw ApiException.conflict("Ya existe un frente con ese nombre");
        }
        f.setNombre(req.nombre().trim());
        f.setUbicacion(req.ubicacion());
        f.setMineral(req.mineral().trim());
        f.setMetaDiariaToneladas(req.metaDiariaToneladas());
        f.setActivo(req.activo() == null || req.activo());
        frentes.save(f);
        audit.registrar(id == null ? "CREAR" : "EDITAR", "FrenteTrabajo", f.getId(), f.getNombre());
        return f;
    }

    // ----------------------------- Produccion -----------------------------

    /** Servicio RegisterProduction (RF031, RF032, RF033). */
    @Transactional
    public ProduccionResponse registrar(ProduccionRequest req) {
        FrenteTrabajo frente = frentes.findById(req.frenteId()).orElseThrow(() -> ApiException.notFound("Frente"));
        if (!frente.getActivo()) {
            throw ApiException.badRequest("El frente de trabajo está inactivo");
        }
        if (producciones.existsByFechaAndTurnoAndFrenteId(req.fecha(), req.turno(), frente.getId())) {
            throw ApiException.conflict("Ya existe producción registrada para " + frente.getNombre() + " el "
                    + Fmt.date(req.fecha()) + " en el turno " + req.turno());
        }
        Produccion p = new Produccion();
        p.setFecha(req.fecha());
        p.setTurno(req.turno());
        p.setFrente(frente);
        p.setToneladas(req.toneladas());
        p.setHorasOperacion(req.horasOperacion() == null ? BigDecimal.valueOf(8) : req.horasOperacion());
        p.setObservaciones(req.observaciones());
        p.setRegistradoPor(AuditService.usuarioActual());
        asignar(p, req.asignaciones());
        producciones.save(p);
        audit.registrar("REGISTRAR", "Producción", p.getId(),
                frente.getNombre() + " " + req.fecha() + " " + req.turno() + " " + Fmt.num(req.toneladas()) + " t");
        return respuesta(p);
    }

    @Transactional
    public ProduccionResponse editar(Long id, ProduccionRequest req) {
        Produccion p = buscar(id);
        boolean claveCambia = !p.getFecha().equals(req.fecha()) || !p.getTurno().equals(req.turno())
                || !p.getFrente().getId().equals(req.frenteId());
        if (claveCambia && producciones.existsByFechaAndTurnoAndFrenteId(req.fecha(), req.turno(), req.frenteId())) {
            throw ApiException.conflict("Ya existe producción para ese frente, fecha y turno");
        }
        p.setFecha(req.fecha());
        p.setTurno(req.turno());
        p.setFrente(frentes.findById(req.frenteId()).orElseThrow(() -> ApiException.notFound("Frente")));
        p.setToneladas(req.toneladas());
        if (req.horasOperacion() != null) {
            p.setHorasOperacion(req.horasOperacion());
        }
        p.setObservaciones(req.observaciones());
        if (req.asignaciones() != null) {
            asignar(p, req.asignaciones());
        }
        audit.registrar("EDITAR", "Producción", id, Fmt.num(req.toneladas()) + " t");
        return respuesta(p);
    }

    /** RF033: reemplaza el personal asignado al turno. */
    @Transactional
    public ProduccionResponse asignarPersonal(Long id, List<AsignacionRequest> lista) {
        Produccion p = buscar(id);
        asignar(p, lista);
        audit.registrar("ASIGNAR_PERSONAL", "Producción", id, lista.size() + " trabajadores");
        return respuesta(p);
    }

    @Transactional
    public void eliminar(Long id) {
        Produccion p = buscar(id);
        producciones.delete(p);
        audit.registrar("ELIMINAR", "Producción", id, p.getFecha() + " " + p.getTurno());
    }

    public Produccion buscar(Long id) {
        return producciones.findById(id).orElseThrow(() -> ApiException.notFound("Registro de producción"));
    }

    public ProduccionResponse obtener(Long id) {
        return respuesta(buscar(id));
    }

    /** Servicio ListProduction. */
    public List<ProduccionResponse> listar(LocalDate desde, LocalDate hasta, Long frenteId) {
        ContabilidadService.validarRango(desde, hasta);
        return producciones.buscar(desde, hasta, frenteId).stream().map(this::respuesta).toList();
    }

    public List<TurnoEmpleado> turnosEmpleado(Long empleadoId, LocalDate desde, LocalDate hasta) {
        return asignaciones.delEmpleado(empleadoId, desde, hasta).stream()
                .map(a -> new TurnoEmpleado(a.getProduccion().getId(), a.getProduccion().getFecha(),
                        a.getProduccion().getTurno(), a.getProduccion().getFrente().getNombre(), a.getLabor(),
                        a.getProduccion().getToneladas()))
                .toList();
    }

    /** RF034: reporte diario de produccion. */
    public ReporteDiario reporteDiario(LocalDate fecha) {
        List<Produccion> lista = producciones.buscar(fecha, fecha, null);
        BigDecimal total = BigDecimal.ZERO;
        int personal = 0;
        List<LineaReporte> lineas = new ArrayList<>();
        Set<Long> frentesDia = new HashSet<>();
        BigDecimal meta = BigDecimal.ZERO;
        for (Produccion p : lista) {
            total = total.add(p.getToneladas());
            personal += p.getAsignaciones().size();
            if (frentesDia.add(p.getFrente().getId())) {
                meta = meta.add(p.getFrente().getMetaDiariaToneladas());
            }
            lineas.add(new LineaReporte(p.getFrente().getNombre(), p.getTurno(), p.getToneladas(),
                    p.getHorasOperacion(), p.getAsignaciones().size(), p.getObservaciones()));
        }
        return new ReporteDiario(fecha, total, meta, porcentaje(total, meta), personal, lineas);
    }

    /** RF035: indicadores de rendimiento en un rango de fechas. */
    public Indicadores indicadores(LocalDate desde, LocalDate hasta) {
        ContabilidadService.validarRango(desde, hasta);
        List<Produccion> lista = producciones.buscar(desde, hasta, null);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal horas = BigDecimal.ZERO;
        int personal = 0;
        Map<String, BigDecimal> turnos = new LinkedHashMap<>();
        turnos.put("MANANA", BigDecimal.ZERO);
        turnos.put("TARDE", BigDecimal.ZERO);
        turnos.put("NOCHE", BigDecimal.ZERO);
        Map<LocalDate, BigDecimal> dias = new TreeMap<>();
        Map<Long, BigDecimal[]> porFrente = new LinkedHashMap<>();
        Map<Long, Set<LocalDate>> diasFrente = new LinkedHashMap<>();
        Map<Long, FrenteTrabajo> frentesMap = new LinkedHashMap<>();
        for (Produccion p : lista) {
            total = total.add(p.getToneladas());
            horas = horas.add(p.getHorasOperacion());
            personal += p.getAsignaciones().size();
            turnos.merge(p.getTurno(), p.getToneladas(), BigDecimal::add);
            dias.merge(p.getFecha(), p.getToneladas(), BigDecimal::add);
            FrenteTrabajo f = p.getFrente();
            frentesMap.put(f.getId(), f);
            porFrente.computeIfAbsent(f.getId(), k -> new BigDecimal[] { BigDecimal.ZERO })[0] = porFrente
                    .get(f.getId())[0].add(p.getToneladas());
            diasFrente.computeIfAbsent(f.getId(), k -> new HashSet<>()).add(p.getFecha());
        }
        List<Cumplimiento> cumplimiento = new ArrayList<>();
        porFrente.forEach((id, v) -> {
            FrenteTrabajo f = frentesMap.get(id);
            BigDecimal meta = f.getMetaDiariaToneladas().multiply(BigDecimal.valueOf(diasFrente.get(id).size()));
            cumplimiento.add(new Cumplimiento(f.getNombre(), v[0], meta, porcentaje(v[0], meta)));
        });
        int numDias = dias.size();
        return new Indicadores(desde, hasta, total,
                numDias == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(numDias), 2, RoundingMode.HALF_UP),
                horas.signum() == 0 ? BigDecimal.ZERO : total.divide(horas, 2, RoundingMode.HALF_UP),
                personal == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(personal), 2, RoundingMode.HALF_UP),
                numDias, lista.size(),
                turnos.entrySet().stream().map(e -> new Valor(e.getKey(), e.getValue())).toList(),
                cumplimiento,
                dias.entrySet().stream().map(e -> new Valor(e.getKey().toString(), e.getValue())).toList());
    }

    /**
     * Sincroniza el personal del turno. Conserva las filas de quienes siguen asignados
     * (Hibernate inserta antes de borrar, asi se evita chocar con la llave unica).
     */
    private void asignar(Produccion p, List<AsignacionRequest> lista) {
        List<AsignacionRequest> nuevas = lista == null ? List.of() : lista;
        Map<Long, String> labores = new LinkedHashMap<>();
        for (AsignacionRequest a : nuevas) {
            if (labores.put(a.empleadoId(), a.labor().trim()) != null) {
                throw ApiException.badRequest("Un trabajador esta asignado dos veces al mismo turno");
            }
        }
        p.getAsignaciones().removeIf(as -> !labores.containsKey(as.getEmpleado().getId()));
        Set<Long> existentes = new HashSet<>();
        for (Produccion.Asignacion as : p.getAsignaciones()) {
            existentes.add(as.getEmpleado().getId());
            as.setLabor(labores.get(as.getEmpleado().getId()));
        }
        labores.forEach((empleadoId, labor) -> {
            if (existentes.contains(empleadoId)) {
                return;
            }
            Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
            if (!e.isActivo()) {
                throw ApiException.badRequest(e.getNombreCompleto() + " está inactivo y no puede asignarse");
            }
            Produccion.Asignacion as = new Produccion.Asignacion();
            as.setProduccion(p);
            as.setEmpleado(e);
            as.setLabor(labor);
            p.getAsignaciones().add(as);
        });
    }

    private ProduccionResponse respuesta(Produccion p) {
        List<AsignacionResponse> as = p.getAsignaciones().stream()
                .map(a -> new AsignacionResponse(a.getId(), a.getEmpleado().getId(), a.getEmpleado().getCedula(),
                        a.getEmpleado().getNombreCompleto(), a.getLabor()))
                .toList();
        FrenteTrabajo f = p.getFrente();
        return new ProduccionResponse(p.getId(), p.getFecha(), p.getTurno(), f.getId(), f.getNombre(), f.getMineral(),
                p.getToneladas(), p.getHorasOperacion(), p.getObservaciones(), p.getRegistradoPor(), p.getCreatedAt(),
                as.size(), as);
    }

    private static BigDecimal porcentaje(BigDecimal valor, BigDecimal meta) {
        return meta.signum() == 0 ? BigDecimal.ZERO : valor.multiply(CIEN).divide(meta, 1, RoundingMode.HALF_UP);
    }
}
