package com.automine.incapacidades;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.Roles;
import com.automine.empleados.Empleado;
import com.automine.incapacidades.IncapacidadDtos.AnulacionRequest;
import com.automine.incapacidades.IncapacidadDtos.IncapacidadRequest;
import com.automine.incapacidades.IncapacidadDtos.IncapacidadResponse;
import com.automine.incapacidades.IncapacidadDtos.ResumenIncapacidades;
import com.automine.incapacidades.IncapacidadDtos.RevisionRequest;
import com.automine.nomina.NominaService;
import com.automine.notificaciones.NotificacionService;
import com.automine.security.UsuarioActual;

import lombok.RequiredArgsConstructor;

/**
 * Registro y revision de incapacidades.
 * - El trabajador registra la suya y adjunta el certificado de la EPS/ARL (obligatorio).
 * - SG-SST, Talento Humano, el supervisor o el administrador pueden registrarla en su nombre.
 * - SG-SST, Talento Humano o el administrador la aprueban o rechazan; al aprobarla
 *   se refleja en la nomina abierta del periodo (dias y valor de la incapacidad).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncapacidadService {

    /** Pueden registrar incapacidades de cualquier trabajador. */
    public static final String[] REGISTRAN_PARA_OTROS = { Roles.ADMIN, Roles.SGSST, Roles.RRHH, Roles.SUPERVISOR };
    /** Aprueban, rechazan, anulan y ven los certificados (datos de salud). */
    public static final String[] REVISAN = { Roles.ADMIN, Roles.SGSST, Roles.RRHH };
    /** Ven el listado de todos los trabajadores (sin el certificado). */
    public static final String[] VEN_TODAS = { Roles.ADMIN, Roles.SGSST, Roles.RRHH, Roles.SUPERVISOR, Roles.GERENTE,
            Roles.CONTADOR };

    private static final Set<String> TIPOS_ARCHIVO = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final int MAX_DIAS = 180;

    private final Incapacidad.Repo incapacidades;
    private final Incapacidad.Soporte.Repo soportes;
    private final Empleado.Repo empleados;
    private final NominaService nomina;
    private final AuditService audit;
    private final NotificacionService notificaciones;

    @Transactional
    public IncapacidadResponse registrar(IncapacidadRequest req, MultipartFile soporte) {
        UsuarioActual u = UsuarioActual.get();
        Long empleadoId;
        if (u.tieneRol(REGISTRAN_PARA_OTROS)) {
            empleadoId = req.empleadoId() != null ? req.empleadoId() : u.empleadoId();
            if (empleadoId == null) {
                throw ApiException.badRequest("Seleccione el trabajador incapacitado");
            }
        } else {
            if (u.empleadoId() == null) {
                throw ApiException.forbidden("Su usuario no está vinculado a un empleado");
            }
            if (req.empleadoId() != null && !req.empleadoId().equals(u.empleadoId())) {
                throw ApiException.forbidden("Solo puede registrar sus propias incapacidades");
            }
            empleadoId = u.empleadoId();
        }
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            throw ApiException.badRequest("El empleado " + e.getNombreCompleto() + " está inactivo");
        }
        validarDatos(req);
        validarArchivo(soporte);
        if (!incapacidades.traslapadas(e.getId(), req.fechaInicio(), req.fechaFin(), -1L).isEmpty()) {
            throw ApiException.conflict("Ya hay una incapacidad registrada para " + e.getNombreCompleto()
                    + " que se cruza con esas fechas");
        }
        Incapacidad i = new Incapacidad();
        i.setEmpleado(e);
        i.setTipo(req.tipo());
        i.setFechaInicio(req.fechaInicio());
        i.setFechaFin(req.fechaFin());
        i.setDias((int) ChronoUnit.DAYS.between(req.fechaInicio(), req.fechaFin()) + 1);
        i.setProrroga(Boolean.TRUE.equals(req.prorroga()));
        i.setEntidad(texto(req.entidad(), 120));
        i.setNumeroCertificado(texto(req.numeroCertificado(), 60));
        i.setObservaciones(texto(req.observaciones(), 1000));
        i.setEstado(Incapacidad.PENDIENTE);
        i.setRegistradoPor(u.nombre());
        incapacidades.save(i);
        guardarSoporte(i, soporte);
        audit.registrar("REGISTRAR", "Incapacidad", i.getId(),
                e.getNombreCompleto() + " " + i.getDias() + " días desde " + Fmt.date(i.getFechaInicio()));
        notificaciones.incapacidadRegistrada(i);
        return IncapacidadResponse.of(i);
    }

    /** El trabajador corrige el certificado mientras no este aprobado; si fue rechazada vuelve a revision. */
    @Transactional
    public IncapacidadResponse reemplazarSoporte(Long id, MultipartFile soporte) {
        Incapacidad i = buscar(id);
        verificarAcceso(i, REGISTRAN_PARA_OTROS);
        if (!Incapacidad.PENDIENTE.equals(i.getEstado()) && !Incapacidad.RECHAZADA.equals(i.getEstado())) {
            throw ApiException.badRequest("Solo se puede cambiar el soporte de una incapacidad pendiente o rechazada");
        }
        validarArchivo(soporte);
        if (Incapacidad.RECHAZADA.equals(i.getEstado())) {
            if (!incapacidades.traslapadas(i.getEmpleado().getId(), i.getFechaInicio(), i.getFechaFin(), i.getId())
                    .isEmpty()) {
                throw ApiException.conflict("Ya hay otra incapacidad registrada en esas fechas");
            }
            i.setEstado(Incapacidad.PENDIENTE);
            i.setMotivoRechazo(null);
            i.setRevisadoPor(null);
            i.setFechaRevision(null);
            notificaciones.incapacidadRegistrada(i);
        }
        guardarSoporte(i, soporte);
        audit.registrar("SOPORTE", "Incapacidad", id, i.getSoporteNombre());
        return IncapacidadResponse.of(i);
    }

    @Transactional
    public IncapacidadResponse revisar(Long id, RevisionRequest req) {
        Incapacidad i = buscar(id);
        if (!Incapacidad.PENDIENTE.equals(i.getEstado())) {
            throw ApiException.badRequest("La incapacidad ya fue revisada (" + i.getEstado().toLowerCase() + ")");
        }
        boolean aprobar = req.aprobar();
        if (!aprobar && (req.motivo() == null || req.motivo().isBlank())) {
            throw ApiException.badRequest("Indique el motivo del rechazo para que el trabajador pueda corregirlo");
        }
        if (aprobar && soportes.findByIncapacidadId(id).isEmpty()) {
            throw ApiException.badRequest("No se puede aprobar sin el certificado de soporte");
        }
        i.setEstado(aprobar ? Incapacidad.APROBADA : Incapacidad.RECHAZADA);
        i.setMotivoRechazo(aprobar ? null : req.motivo().trim());
        i.setRevisadoPor(UsuarioActual.get().nombre());
        i.setFechaRevision(LocalDateTime.now());
        incapacidades.save(i);
        String aviso = aprobar ? nomina.recalcularIncapacidades(i.getEmpleado().getId(), i.getFechaInicio(), i.getFechaFin())
                : null;
        audit.registrar(aprobar ? "APROBAR" : "RECHAZAR", "Incapacidad", id,
                i.getEmpleado().getNombreCompleto() + (aprobar ? "" : ": " + i.getMotivoRechazo()));
        notificaciones.incapacidadRevisada(i);
        return IncapacidadResponse.of(i, aviso);
    }

    @Transactional
    public IncapacidadResponse anular(Long id, AnulacionRequest req) {
        Incapacidad i = buscar(id);
        if (Incapacidad.ANULADA.equals(i.getEstado())) {
            throw ApiException.badRequest("La incapacidad ya está anulada");
        }
        boolean estabaAprobada = Incapacidad.APROBADA.equals(i.getEstado());
        i.setEstado(Incapacidad.ANULADA);
        i.setMotivoRechazo(req.motivo().trim());
        i.setRevisadoPor(UsuarioActual.get().nombre());
        i.setFechaRevision(LocalDateTime.now());
        incapacidades.save(i);
        String aviso = estabaAprobada
                ? nomina.recalcularIncapacidades(i.getEmpleado().getId(), i.getFechaInicio(), i.getFechaFin())
                : null;
        audit.registrar("ANULAR", "Incapacidad", id, i.getEmpleado().getNombreCompleto() + ": " + req.motivo());
        return IncapacidadResponse.of(i, aviso);
    }

    public List<IncapacidadResponse> listar(Long empleadoId, String estado) {
        UsuarioActual u = UsuarioActual.get();
        Long filtro = empleadoId;
        if (!u.tieneRol(VEN_TODAS)) {
            if (u.empleadoId() == null) {
                return List.of();
            }
            filtro = u.empleadoId();
        }
        String est = estado == null || estado.isBlank() ? null : estado;
        return incapacidades.buscar(filtro, est).stream().map(IncapacidadResponse::of).toList();
    }

    public IncapacidadResponse obtener(Long id) {
        Incapacidad i = buscar(id);
        verificarAcceso(i, VEN_TODAS);
        return IncapacidadResponse.of(i);
    }

    /** El certificado contiene datos de salud: solo el trabajador y quienes revisan. */
    public Incapacidad.Soporte soporte(Long id) {
        Incapacidad i = buscar(id);
        verificarAcceso(i, REVISAN);
        Incapacidad.Soporte s = soportes.findByIncapacidadId(id).orElseThrow(() -> ApiException.notFound("Soporte"));
        s.getDatos();
        return s;
    }

    public ResumenIncapacidades resumen() {
        return new ResumenIncapacidades(incapacidades.countByEstado(Incapacidad.PENDIENTE));
    }

    private Incapacidad buscar(Long id) {
        return incapacidades.findWithEmpleadoById(id).orElseThrow(() -> ApiException.notFound("Incapacidad"));
    }

    private static void verificarAcceso(Incapacidad i, String... rolesConAccesoTotal) {
        UsuarioActual.verificarEmpleado(i.getEmpleado().getId(), rolesConAccesoTotal);
    }

    private static void validarDatos(IncapacidadRequest req) {
        if (req.tipo() == null || !Incapacidad.TIPOS.contains(req.tipo())) {
            throw ApiException.badRequest("Seleccione el tipo de incapacidad");
        }
        if (req.fechaInicio() == null || req.fechaFin() == null) {
            throw ApiException.badRequest("Indique la fecha de inicio y de fin de la incapacidad");
        }
        if (req.fechaFin().isBefore(req.fechaInicio())) {
            throw ApiException.badRequest("La fecha de fin no puede ser anterior a la de inicio");
        }
        long dias = ChronoUnit.DAYS.between(req.fechaInicio(), req.fechaFin()) + 1;
        if (dias > MAX_DIAS) {
            throw ApiException.badRequest("Un certificado no puede superar " + MAX_DIAS + " días; registre la prórroga aparte");
        }
        if (req.fechaInicio().isAfter(LocalDate.now().plusDays(30))) {
            throw ApiException.badRequest("La fecha de inicio está demasiado lejos en el futuro");
        }
        if (req.fechaInicio().isBefore(LocalDate.now().minusYears(1))) {
            throw ApiException.badRequest("La incapacidad tiene más de un año; consúltelo con Talento Humano");
        }
    }

    private static void validarArchivo(MultipartFile f) {
        if (f == null || f.isEmpty()) {
            throw ApiException.badRequest("Adjunte el certificado de incapacidad (PDF o foto)");
        }
        if (!TIPOS_ARCHIVO.contains(f.getContentType())) {
            throw ApiException.badRequest("El soporte debe ser PDF, JPG o PNG");
        }
    }

    private void guardarSoporte(Incapacidad i, MultipartFile f) {
        Incapacidad.Soporte s = soportes.findByIncapacidadId(i.getId()).orElseGet(Incapacidad.Soporte::new);
        s.setIncapacidadId(i.getId());
        String nombre = f.getOriginalFilename() == null || f.getOriginalFilename().isBlank() ? "soporte"
                : f.getOriginalFilename();
        s.setNombre(nombre.length() > 200 ? nombre.substring(nombre.length() - 200) : nombre);
        s.setContentType(f.getContentType());
        try {
            s.setDatos(f.getBytes());
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
        soportes.save(s);
        i.setSoporteNombre(s.getNombre());
    }

    private static String texto(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
