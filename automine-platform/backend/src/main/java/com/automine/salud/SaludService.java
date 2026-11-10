package com.automine.salud;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Roles;
import com.automine.empleados.Empleado;
import com.automine.notificaciones.NotificacionService;
import com.automine.security.UsuarioActual;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Salud ocupacional: evaluaciones medicas con su vencimiento, alertas y los indicadores
 * minimos del SG-SST (Resolucion 0312 de 2019, art. 30): frecuencia y severidad de la
 * accidentalidad, mortalidad y ausentismo por causa medica.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaludService {

    public static final String[] GESTIONAN = { Roles.ADMIN, Roles.SGSST, Roles.RRHH };
    private static final Set<String> ARCHIVOS = Set.of("application/pdf", "image/jpeg", "image/png");

    private final ExamenMedico.Repo examenes;
    private final Empleado.Repo empleados;
    private final AuditService audit;
    private final NotificacionService notificaciones;
    private final EntityManager em;

    // ------------------------------ Examenes ------------------------------

    public record ExamenRequest(Long empleadoId, String tipo, LocalDate fecha, String concepto, String restricciones,
            String recomendaciones, String ips, String medico, LocalDate proximaFecha) {
    }

    public record ExamenResponse(Long id, Long empleadoId, String cedula, String empleado, String cargo, String tipo,
            LocalDate fecha, String concepto, String restricciones, String recomendaciones, String ips, String medico,
            LocalDate proximaFecha, String soporteNombre, String registradoPor, String vigencia) {

        static ExamenResponse of(ExamenMedico x) {
            var e = x.getEmpleado();
            return new ExamenResponse(x.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(),
                    x.getTipo(), x.getFecha(), x.getConcepto(), x.getRestricciones(), x.getRecomendaciones(), x.getIps(),
                    x.getMedico(), x.getProximaFecha(), x.getSoporteNombre(), x.getRegistradoPor(), SaludService.vigencia(x.getProximaFecha()));
        }
    }

    /** Estado de cada trabajador activo frente a sus examenes. */
    public record EstadoExamen(Long empleadoId, String cedula, String empleado, String cargo, String estado,
            LocalDate ultimoExamen, String ultimoTipo, String concepto, String restricciones, LocalDate proximaFecha,
            Long diasRestantes) {
    }

    static String vigencia(LocalDate proxima) {
        if (proxima == null) {
            return "SIN_FECHA";
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), proxima);
        return dias < 0 ? "VENCIDO" : dias <= 30 ? "POR_VENCER" : "VIGENTE";
    }

    @Transactional
    public ExamenResponse registrar(ExamenRequest req, MultipartFile soporte) {
        if (req.empleadoId() == null) {
            throw ApiException.badRequest("Seleccione el trabajador");
        }
        Empleado e = empleados.findById(req.empleadoId()).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (req.tipo() == null || !ExamenMedico.TIPOS.contains(req.tipo())) {
            throw ApiException.badRequest("Seleccione el tipo de evaluación médica");
        }
        if (req.concepto() == null || !ExamenMedico.CONCEPTOS.contains(req.concepto())) {
            throw ApiException.badRequest("Seleccione el concepto de aptitud");
        }
        if (req.fecha() == null || req.fecha().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Indique la fecha del examen (no puede ser futura)");
        }
        if ("APTO_CON_RESTRICCIONES".equals(req.concepto()) && (req.restricciones() == null || req.restricciones().isBlank())) {
            throw ApiException.badRequest("Escriba las restricciones laborales indicadas por el médico");
        }
        if (req.proximaFecha() != null && !req.proximaFecha().isAfter(req.fecha())) {
            throw ApiException.badRequest("La próxima evaluación debe ser posterior a la fecha del examen");
        }
        ExamenMedico x = new ExamenMedico();
        x.setEmpleado(e);
        x.setTipo(req.tipo());
        x.setFecha(req.fecha());
        x.setConcepto(req.concepto());
        x.setRestricciones(texto(req.restricciones()));
        x.setRecomendaciones(texto(req.recomendaciones()));
        x.setIps(texto(req.ips()));
        x.setMedico(texto(req.medico()));
        // Si no se indica, el periodico es anual (salvo el de retiro)
        x.setProximaFecha(req.proximaFecha() != null ? req.proximaFecha()
                : "RETIRO".equals(req.tipo()) ? null : req.fecha().plusYears(1));
        if (soporte != null && !soporte.isEmpty()) {
            if (!ARCHIVOS.contains(soporte.getContentType())) {
                throw ApiException.badRequest("El certificado debe ser PDF, JPG o PNG");
            }
            try {
                x.setSoporte(soporte.getBytes());
            } catch (java.io.IOException ex) {
                throw ApiException.badRequest("No se pudo leer el archivo");
            }
            x.setSoporteNombre(soporte.getOriginalFilename() == null ? "certificado" : soporte.getOriginalFilename());
            x.setSoporteTipo(soporte.getContentType());
        }
        x.setRegistradoPor(UsuarioActual.get().nombre());
        examenes.save(x);
        audit.registrar("EXAMEN", "Empleado", e.getId(), req.tipo() + " " + req.concepto());
        return ExamenResponse.of(x);
    }

    public List<ExamenResponse> listar(Long empleadoId) {
        UsuarioActual u = UsuarioActual.get();
        Long filtro = empleadoId;
        if (!u.tieneRol(GESTIONAN) && !u.tieneRol(Roles.GERENTE, Roles.SUPERVISOR)) {
            if (u.empleadoId() == null) {
                return List.of();
            }
            filtro = u.empleadoId();
        }
        return examenes.buscar(filtro).stream().map(ExamenResponse::of).toList();
    }

    public ExamenMedico soporte(Long id) {
        ExamenMedico x = examenes.findWithEmpleadoById(id).orElseThrow(() -> ApiException.notFound("Examen"));
        UsuarioActual.verificarEmpleado(x.getEmpleado().getId(), GESTIONAN);
        if (x.getSoporte() == null) {
            throw ApiException.notFound("Certificado");
        }
        return x;
    }

    /** Estado de examenes por trabajador activo: sin examen, vencido, por vencer o vigente. */
    public List<EstadoExamen> estado() {
        Map<Long, ExamenMedico> ultimo = new LinkedHashMap<>();
        for (ExamenMedico x : examenes.deActivos()) {
            ultimo.putIfAbsent(x.getEmpleado().getId(), x);
        }
        List<EstadoExamen> res = new ArrayList<>();
        for (Empleado e : empleados.findByEstadoOrderByApellidosAsc(Empleado.ACTIVO)) {
            ExamenMedico x = ultimo.get(e.getId());
            if (x == null) {
                res.add(new EstadoExamen(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), "SIN_EXAMEN", null,
                        null, null, null, null, null));
            } else {
                Long dias = x.getProximaFecha() == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), x.getProximaFecha());
                res.add(new EstadoExamen(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(),
                        vigencia(x.getProximaFecha()), x.getFecha(), x.getTipo(), x.getConcepto(), x.getRestricciones(),
                        x.getProximaFecha(), dias));
            }
        }
        return res;
    }

    /** Correo semanal (lunes 7 a. m.) a SG-SST con examenes vencidos, por vencer o faltantes. */
    @Scheduled(cron = "0 0 7 * * MON", zone = "America/Bogota")
    public void alertaSemanal() {
        List<EstadoExamen> pendientes = estado().stream()
                .filter(x -> !"VIGENTE".equals(x.estado()) && !"SIN_FECHA".equals(x.estado())).toList();
        if (!pendientes.isEmpty()) {
            notificaciones.examenesPendientes(pendientes.stream()
                    .map(x -> x.empleado() + " — " + switch (x.estado()) {
                        case "SIN_EXAMEN" -> "sin examen de ingreso registrado";
                        case "VENCIDO" -> "vencido desde " + com.automine.common.Fmt.date(x.proximaFecha());
                        default -> "vence el " + com.automine.common.Fmt.date(x.proximaFecha());
                    }).toList());
        }
    }

    // ------------------------------ Indicadores ------------------------------

    public record IndicadorMes(int anio, int mes, int trabajadores, long accidentes, long diasPerdidosAt,
            long accidentesMortales, long diasIncapacidad, long diasProgramados, BigDecimal frecuencia,
            BigDecimal severidad, BigDecimal ausentismo) {
    }

    public record Indicadores(int anio, List<IndicadorMes> meses, BigDecimal frecuenciaAnual, BigDecimal severidadAnual,
            BigDecimal mortalidad, BigDecimal ausentismoAnual, long accidentes, long diasIncapacidad,
            Map<String, Long> diasPorTipo) {
    }

    /**
     * Indicadores minimos (Res. 0312/2019):
     * frecuencia = AT del mes / trabajadores x 100; severidad = dias perdidos por AT / trabajadores x 100;
     * mortalidad = AT mortales / AT del año x 100; ausentismo = dias de incapacidad / dias programados x 100.
     */
    public Indicadores indicadores(int anio) {
        List<IndicadorMes> meses = new ArrayList<>();
        long accAnio = 0, mortAnio = 0, diasAtAnio = 0, incapAnio = 0, progAnio = 0;
        double trabProm = 0;
        int mesesConDatos = 0;
        LocalDate hoy = LocalDate.now();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(anio, m);
            if (ym.atDay(1).isAfter(hoy)) {
                break;
            }
            LocalDate ini = ym.atDay(1), fin = ym.atEndOfMonth();
            int trab = ((Number) em.createQuery("""
                    select count(e) from Empleado e
                    where e.fechaIngreso <= :fin and (e.fechaRetiro is null or e.fechaRetiro >= :ini)""")
                    .setParameter("ini", ini).setParameter("fin", fin).getSingleResult()).intValue();
            Object[] at = (Object[]) em.createQuery("""
                    select count(x), coalesce(sum(x.diasIncapacidad), 0),
                           coalesce(sum(case when x.gravedad = 'MORTAL' then 1 else 0 end), 0)
                    from EventoSst x where x.tipo = 'ACCIDENTE' and x.fecha >= :ini and x.fecha < :fin""")
                    .setParameter("ini", ini.atStartOfDay()).setParameter("fin", fin.plusDays(1).atStartOfDay())
                    .getSingleResult();
            long acc = ((Number) at[0]).longValue();
            long diasAt = ((Number) at[1]).longValue();
            long mort = ((Number) at[2]).longValue();
            long incap = diasIncapacidad(ini, fin);
            long programados = (long) trab * 30;
            meses.add(new IndicadorMes(anio, m, trab, acc, diasAt, mort, incap, programados, pct(acc, trab),
                    pct(diasAt, trab), pct(incap, programados)));
            accAnio += acc;
            mortAnio += mort;
            diasAtAnio += diasAt;
            incapAnio += incap;
            progAnio += programados;
            trabProm += trab;
            mesesConDatos++;
        }
        int promedio = mesesConDatos == 0 ? 0 : (int) Math.round(trabProm / mesesConDatos);
        return new Indicadores(anio, meses, pct(accAnio, promedio), pct(diasAtAnio, promedio), pct(mortAnio, accAnio),
                pct(incapAnio, progAnio), accAnio, incapAnio, diasPorTipo(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31)));
    }

    /** Dias de incapacidad aprobada (todas las causas) que caen dentro del rango. */
    private long diasIncapacidad(LocalDate ini, LocalDate fin) {
        return diasPorTipo(ini, fin).values().stream().mapToLong(Long::longValue).sum();
    }

    private Map<String, Long> diasPorTipo(LocalDate ini, LocalDate fin) {
        List<Object[]> filas = em.createQuery("""
                select i.tipo, i.fechaInicio, i.fechaFin from Incapacidad i
                where i.estado = 'APROBADA' and i.fechaInicio <= :fin and i.fechaFin >= :ini""", Object[].class)
                .setParameter("ini", ini).setParameter("fin", fin).getResultList();
        Map<String, Long> res = new LinkedHashMap<>();
        for (Object[] f : filas) {
            LocalDate d = ((LocalDate) f[1]).isBefore(ini) ? ini : (LocalDate) f[1];
            LocalDate h = ((LocalDate) f[2]).isAfter(fin) ? fin : (LocalDate) f[2];
            res.merge((String) f[0], ChronoUnit.DAYS.between(d, h) + 1, Long::sum);
        }
        return res;
    }

    private static BigDecimal pct(long a, long b) {
        if (b == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_UP);
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim().length() > 1000 ? s.trim().substring(0, 1000) : s.trim();
    }

    @SuppressWarnings("unused")
    private static LocalDateTime ahora() {
        return LocalDateTime.now();
    }
}
