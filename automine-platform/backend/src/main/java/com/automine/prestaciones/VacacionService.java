package com.automine.prestaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.ParametroService;
import com.automine.common.Roles;
import com.automine.empleados.Empleado;
import com.automine.nomina.NominaService;
import com.automine.notificaciones.NotificacionService;
import com.automine.prestaciones.PrestacionesDtos.RevisionRequest;
import com.automine.prestaciones.PrestacionesDtos.SaldoVacaciones;
import com.automine.prestaciones.PrestacionesDtos.SimulacionVacaciones;
import com.automine.prestaciones.PrestacionesDtos.VacacionRequest;
import com.automine.prestaciones.PrestacionesDtos.VacacionResponse;
import com.automine.security.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * Vacaciones: el trabajador las solicita (o Talento Humano en su nombre) indicando el
 * primer dia y los dias habiles; Talento Humano las aprueba. Las aprobadas se pagan en
 * la nomina del mes como "vacaciones" (sin auxilio de transporte).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacacionService {

    public static final String[] GESTIONAN = { Roles.ADMIN, Roles.RRHH };
    public static final String[] VEN_TODAS = { Roles.ADMIN, Roles.RRHH, Roles.GERENTE, Roles.CONTADOR, Roles.SUPERVISOR };

    private final Vacacion.Repo vacaciones;
    private final Empleado.Repo empleados;
    private final ParametroService parametros;
    private final NominaService nomina;
    private final AuditService audit;
    private final NotificacionService notificaciones;

    public boolean sabadoHabil() {
        try {
            return parametros.decimal("SABADO_HABIL").signum() != 0;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public SimulacionVacaciones simular(LocalDate inicio, int habiles) {
        boolean sab = sabadoHabil();
        if (!Festivos.esHabil(inicio, sab)) {
            throw ApiException.badRequest("Las vacaciones deben empezar en un día hábil (" + Fmt.date(inicio)
                    + " es domingo, festivo o sábado no laborable)");
        }
        LocalDate fin = Festivos.finVacaciones(inicio, habiles, sab);
        return new SimulacionVacaciones(inicio, fin, fin.plusDays(1), habiles,
                (int) ChronoUnit.DAYS.between(inicio, fin) + 1);
    }

    public SaldoVacaciones saldo(Long empleadoId) {
        UsuarioActual.verificarEmpleado(empleadoId, VEN_TODAS);
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        return saldo(e);
    }

    SaldoVacaciones saldo(Empleado e) {
        LocalDate hasta = e.getFechaRetiro() != null ? e.getFechaRetiro() : LocalDate.now();
        int laborados = CalculadoraPrestaciones.dias360(e.getFechaIngreso(), hasta);
        BigDecimal causados = CalculadoraPrestaciones.diasVacacionesCausados(laborados);
        long disfrutados = vacaciones.diasDisfrutados(e.getId());
        long comprometidos = vacaciones.diasComprometidos(e.getId());
        return new SaldoVacaciones(e.getId(), e.getNombreCompleto(), e.getFechaIngreso(), laborados, causados,
                disfrutados, comprometidos - disfrutados, causados.subtract(BigDecimal.valueOf(comprometidos)),
                CalculadoraPrestaciones.aplica(e.getTipoContrato()));
    }

    @Transactional
    public VacacionResponse solicitar(VacacionRequest req) {
        UsuarioActual u = UsuarioActual.get();
        Long empleadoId;
        if (u.tieneRol(GESTIONAN)) {
            empleadoId = req.empleadoId() != null ? req.empleadoId() : u.empleadoId();
        } else {
            if (req.empleadoId() != null && !req.empleadoId().equals(u.empleadoId())) {
                throw ApiException.forbidden("Solo puede solicitar sus propias vacaciones");
            }
            empleadoId = u.empleadoId();
        }
        if (empleadoId == null) {
            throw ApiException.badRequest(u.tieneRol(GESTIONAN) ? "Seleccione el trabajador"
                    : "Su usuario no está vinculado a un empleado");
        }
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            throw ApiException.badRequest("El empleado está inactivo");
        }
        if (!CalculadoraPrestaciones.aplica(e.getTipoContrato())) {
            throw ApiException.badRequest("El contrato de " + e.getNombreCompleto()
                    + " (" + e.getTipoContrato().toLowerCase().replace('_', ' ') + ") no genera vacaciones");
        }
        if (req.fechaInicio().isBefore(LocalDate.now().minusDays(60))) {
            throw ApiException.badRequest("La fecha de inicio es muy antigua");
        }
        SimulacionVacaciones sim = simular(req.fechaInicio(), req.diasHabiles());
        SaldoVacaciones saldo = saldo(e);
        if (BigDecimal.valueOf(req.diasHabiles()).compareTo(saldo.diasDisponibles()) > 0) {
            throw ApiException.badRequest("Solo tiene " + saldo.diasDisponibles().stripTrailingZeros().toPlainString()
                    + " días hábiles disponibles (causados " + saldo.diasCausados().stripTrailingZeros().toPlainString()
                    + ", ya tomados o solicitados " + (saldo.diasDisfrutados() + saldo.diasSolicitados()) + ")");
        }
        if (!vacaciones.traslapadas(e.getId(), sim.fechaInicio(), sim.fechaFin(), -1L).isEmpty()) {
            throw ApiException.conflict("Ya tiene vacaciones solicitadas o aprobadas en esas fechas");
        }
        Vacacion v = new Vacacion();
        v.setEmpleado(e);
        v.setFechaInicio(sim.fechaInicio());
        v.setFechaFin(sim.fechaFin());
        v.setFechaRegreso(sim.fechaRegreso());
        v.setDiasHabiles(req.diasHabiles());
        v.setObservaciones(req.observaciones() == null || req.observaciones().isBlank() ? null : req.observaciones().trim());
        v.setEstado(Vacacion.PENDIENTE);
        v.setSolicitadoPor(u.nombre());
        vacaciones.save(v);
        audit.registrar("SOLICITAR", "Vacaciones", v.getId(), e.getNombreCompleto() + " " + req.diasHabiles()
                + " días desde " + Fmt.date(v.getFechaInicio()));
        notificaciones.vacacionesSolicitadas(v);
        return VacacionResponse.of(v);
    }

    @Transactional
    public VacacionResponse revisar(Long id, RevisionRequest req) {
        Vacacion v = buscar(id);
        if (!Vacacion.PENDIENTE.equals(v.getEstado())) {
            throw ApiException.badRequest("La solicitud ya fue revisada");
        }
        boolean aprobar = req.aprobar();
        if (!aprobar && (req.motivo() == null || req.motivo().isBlank())) {
            throw ApiException.badRequest("Indique el motivo del rechazo");
        }
        v.setEstado(aprobar ? Vacacion.APROBADA : Vacacion.RECHAZADA);
        v.setMotivoRechazo(aprobar ? null : req.motivo().trim());
        v.setRevisadoPor(UsuarioActual.get().nombre());
        v.setFechaRevision(LocalDateTime.now());
        vacaciones.save(v);
        String aviso = aprobar
                ? nomina.recalcularNovedades(v.getEmpleado().getId(), v.getFechaInicio(), v.getFechaFin())
                : null;
        audit.registrar(aprobar ? "APROBAR" : "RECHAZAR", "Vacaciones", id, v.getEmpleado().getNombreCompleto());
        notificaciones.vacacionesRevisadas(v);
        return VacacionResponse.of(v, aviso);
    }

    /** El trabajador cancela su solicitud pendiente; Talento Humano puede anular una aprobada. */
    @Transactional
    public VacacionResponse anular(Long id, String motivo) {
        Vacacion v = buscar(id);
        UsuarioActual u = UsuarioActual.get();
        boolean gestiona = u.tieneRol(GESTIONAN);
        boolean propia = v.getEmpleado().getId().equals(u.empleadoId());
        if (Vacacion.ANULADA.equals(v.getEstado()) || Vacacion.RECHAZADA.equals(v.getEstado())) {
            throw ApiException.badRequest("La solicitud ya está " + v.getEstado().toLowerCase());
        }
        if (!gestiona && !(propia && Vacacion.PENDIENTE.equals(v.getEstado()))) {
            throw ApiException.forbidden("Solo puede cancelar sus solicitudes pendientes");
        }
        boolean estabaAprobada = Vacacion.APROBADA.equals(v.getEstado());
        v.setEstado(Vacacion.ANULADA);
        v.setMotivoRechazo(motivo == null || motivo.isBlank() ? "Cancelada" : motivo.trim());
        v.setRevisadoPor(u.nombre());
        v.setFechaRevision(LocalDateTime.now());
        String aviso = estabaAprobada
                ? nomina.recalcularNovedades(v.getEmpleado().getId(), v.getFechaInicio(), v.getFechaFin())
                : null;
        audit.registrar("ANULAR", "Vacaciones", id, v.getMotivoRechazo());
        return VacacionResponse.of(v, aviso);
    }

    public List<VacacionResponse> listar(Long empleadoId, String estado) {
        UsuarioActual u = UsuarioActual.get();
        Long filtro = empleadoId;
        if (!u.tieneRol(VEN_TODAS)) {
            if (u.empleadoId() == null) {
                return List.of();
            }
            filtro = u.empleadoId();
        }
        return vacaciones.buscar(filtro, estado == null || estado.isBlank() ? null : estado).stream()
                .map(VacacionResponse::of).toList();
    }

    public long pendientes() {
        return vacaciones.countByEstado(Vacacion.PENDIENTE);
    }

    private Vacacion buscar(Long id) {
        return vacaciones.findWithEmpleadoById(id).orElseThrow(() -> ApiException.notFound("Vacaciones"));
    }
}
