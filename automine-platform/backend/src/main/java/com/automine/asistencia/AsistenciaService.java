package com.automine.asistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.automine.asistencia.AsistenciaDtos.EstadoRostro;
import com.automine.asistencia.AsistenciaDtos.MarcacionResponse;
import com.automine.asistencia.AsistenciaDtos.PlantillaRostro;
import com.automine.asistencia.AsistenciaDtos.ResultadoNomina;
import com.automine.asistencia.AsistenciaDtos.ResumenEmpleado;
import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.ParametroService;
import com.automine.empleados.Empleado;
import com.automine.nomina.NominaService;
import com.automine.security.UsuarioActual;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Control de asistencia: SG-SST registra la entrada y salida de cada trabajador
 * verificando su rostro en el celular. Las horas extra del mes se pueden pasar a la nomina.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsistenciaService {

    public static final String AUTORIZACION = "Autorizo a la empresa a recolectar y tratar mi imagen facial "
            + "(dato biométrico sensible) con la única finalidad de registrar mi entrada y salida del trabajo, "
            + "conforme a la Ley 1581 de 2012. Sé que puedo consultarla, actualizarla o pedir su eliminación, "
            + "y que se eliminará al terminar mi contrato.";
    private static final Set<String> IMAGENES = Set.of("image/jpeg", "image/png");

    private final Rostro.Repo rostros;
    private final Marcacion.Repo marcaciones;
    private final Empleado.Repo empleados;
    private final ParametroService parametros;
    private final NominaService nomina;
    private final AuditService audit;
    private final ObjectMapper json;

    // ------------------------------ Rostros ------------------------------

    @Transactional
    public EstadoRostro registrarRostro(Long empleadoId, String plantilla, String version, boolean autoriza,
            MultipartFile foto) {
        if (!autoriza) {
            throw ApiException.badRequest("El trabajador debe autorizar el tratamiento de su imagen facial (Ley 1581)");
        }
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            throw ApiException.badRequest("El empleado está inactivo");
        }
        validarPlantilla(plantilla);
        validarImagen(foto, true);
        Rostro r = rostros.findByEmpleadoId(empleadoId).orElseGet(Rostro::new);
        com.automine.common.ArchivoStore.borrar(r.getFoto()); // la foto anterior se reemplaza
        r.setEmpleado(e);
        r.setPlantilla(plantilla);
        r.setVersionModelo(version == null || version.isBlank() ? "geo-v1" : version.trim());
        r.setFoto(bytes(foto));
        r.setAutorizacionFecha(LocalDateTime.now());
        r.setAutorizacionTexto(AUTORIZACION);
        r.setRegistradoPor(UsuarioActual.get().nombre());
        rostros.save(r);
        audit.registrar("REGISTRAR_ROSTRO", "Empleado", empleadoId, "Autorización Ley 1581 aceptada");
        return new EstadoRostro(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), true, r.getCreatedAt(),
                r.getRegistradoPor());
    }

    @Transactional
    public void borrarRostro(Long empleadoId) {
        rostros.findByEmpleadoId(empleadoId).ifPresent(r -> com.automine.common.ArchivoStore.borrar(r.getFoto()));
        if (rostros.borrarDeEmpleado(empleadoId) > 0) {
            audit.registrar("BORRAR_ROSTRO", "Empleado", empleadoId, "Plantilla facial eliminada");
        }
    }

    public List<PlantillaRostro> plantillas() {
        return rostros.activos().stream().map(r -> {
            Empleado e = r.getEmpleado();
            return new PlantillaRostro(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), r.getPlantilla(),
                    r.getVersionModelo(), r.getCreatedAt());
        }).toList();
    }

    public List<EstadoRostro> estadoRostros() {
        Map<Long, Rostro> registrados = new LinkedHashMap<>();
        rostros.activos().forEach(r -> registrados.put(r.getEmpleado().getId(), r));
        return empleados.findByEstadoOrderByApellidosAsc(Empleado.ACTIVO).stream().map(e -> {
            Rostro r = registrados.get(e.getId());
            return new EstadoRostro(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), r != null,
                    r == null ? null : r.getCreatedAt(), r == null ? null : r.getRegistradoPor());
        }).toList();
    }

    public byte[] fotoRostro(Long empleadoId) {
        return rostros.findByEmpleadoId(empleadoId).map(r -> com.automine.common.ArchivoStore.contenido(r.getFoto()))
                .orElseThrow(() -> ApiException.notFound("Rostro registrado"));
    }

    // ------------------------------ Marcaciones ------------------------------

    @Transactional
    public MarcacionResponse marcar(Long empleadoId, String tipo, LocalDateTime fechaHora, BigDecimal similitud,
            String observacion, String dispositivoId, MultipartFile foto) {
        if (dispositivoId != null && !dispositivoId.isBlank()) {
            var previa = marcaciones.findByDispositivoId(dispositivoId.trim());
            if (previa.isPresent()) {
                return MarcacionResponse.of(previa.get(), "Ya estaba sincronizada");
            }
        }
        if (!Marcacion.ENTRADA.equals(tipo) && !Marcacion.SALIDA.equals(tipo)) {
            throw ApiException.badRequest("Indique si es ENTRADA o SALIDA");
        }
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            throw ApiException.badRequest(e.getNombreCompleto() + " está inactivo");
        }
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime cuando = fechaHora == null ? ahora : fechaHora.withSecond(0).withNano(0);
        if (cuando.isAfter(ahora.plusMinutes(10))) {
            throw ApiException.badRequest("La hora de la marcación está en el futuro: revise la hora del celular");
        }
        if (cuando.isBefore(ahora.minusDays(7))) {
            throw ApiException.badRequest("La marcación tiene más de 7 días; regístrela como novedad en Talento Humano");
        }
        boolean conFoto = foto != null && !foto.isEmpty();
        validarImagen(foto, false);
        String metodo;
        if (!conFoto) {
            if (observacion == null || observacion.isBlank()) {
                throw ApiException.badRequest("Sin foto, indique el motivo de la marcación manual");
            }
            metodo = Marcacion.MANUAL;
        } else {
            BigDecimal umbral = umbral();
            metodo = similitud != null && similitud.compareTo(umbral) >= 0 ? Marcacion.FACIAL
                    : Marcacion.VERIFICACION_MANUAL;
        }
        String aviso = marcaciones.findFirstByEmpleadoIdAndAnuladaFalseAndFechaHoraBeforeOrderByFechaHoraDesc(e.getId(), cuando)
                .filter(m -> m.getTipo().equals(tipo) && m.getFechaHora().isAfter(cuando.minusHours(20)))
                .map(m -> "La marcación anterior también fue " + tipo.toLowerCase() + " (" + Fmt.dateTime(m.getFechaHora())
                        + "); revise si falta una " + (Marcacion.ENTRADA.equals(tipo) ? "salida" : "entrada"))
                .orElse(null);
        Marcacion m = new Marcacion();
        m.setEmpleado(e);
        m.setTipo(tipo);
        m.setFechaHora(cuando);
        m.setMetodo(metodo);
        m.setSimilitud(similitud == null ? null : similitud.max(BigDecimal.ZERO).min(BigDecimal.ONE));
        m.setFoto(conFoto ? bytes(foto) : null);
        m.setObservacion(observacion == null || observacion.isBlank() ? null : observacion.trim());
        m.setRegistradoPor(UsuarioActual.get().nombre());
        m.setDispositivoId(dispositivoId == null || dispositivoId.isBlank() ? null : dispositivoId.trim());
        marcaciones.save(m);
        return MarcacionResponse.of(m, aviso);
    }

    @Transactional
    public MarcacionResponse anular(Long id, String motivo) {
        Marcacion m = marcaciones.findWithEmpleadoById(id).orElseThrow(() -> ApiException.notFound("Marcación"));
        m.setAnulada(true);
        m.setObservacion((m.getObservacion() == null ? "" : m.getObservacion() + " · ") + "Anulada: " + motivo.trim());
        audit.registrar("ANULAR", "Marcación", id, motivo);
        return MarcacionResponse.of(m, null);
    }

    public List<MarcacionResponse> delDia(LocalDate fecha, Long empleadoId) {
        return marcaciones.entre(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay(), empleadoId).stream()
                .map(m -> MarcacionResponse.of(m, null)).toList();
    }

    public byte[] fotoMarcacion(Long id) {
        Marcacion m = marcaciones.findById(id).orElseThrow(() -> ApiException.notFound("Marcación"));
        if (m.getFoto() == null) {
            throw ApiException.notFound("Foto");
        }
        return com.automine.common.ArchivoStore.contenido(m.getFoto());
    }

    /** Horas trabajadas y extra del mes por trabajador. */
    public List<ResumenEmpleado> resumen(int anio, int mes, Long empleadoId) {
        if (empleadoId != null) {
            UsuarioActual.verificarEmpleado(empleadoId, "SGSST", "RRHH", "CONTADOR", "GERENTE", "SUPERVISOR");
        }
        YearMonth ym = YearMonth.of(anio, mes);
        // Se incluye el dia siguiente al fin de mes para cerrar los turnos nocturnos del ultimo dia
        List<Marcacion> lista = marcaciones.entre(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atTime(12, 0),
                empleadoId);
        Map<Long, List<Marcacion>> porEmpleado = new LinkedHashMap<>();
        for (Marcacion m : lista) {
            if (!m.isAnulada()) {
                porEmpleado.computeIfAbsent(m.getEmpleado().getId(), k -> new ArrayList<>()).add(m);
            }
        }
        CalculadoraJornada.Parametros p = parametrosJornada();
        List<ResumenEmpleado> res = new ArrayList<>();
        for (var entry : porEmpleado.entrySet()) {
            List<Marcacion> ms = entry.getValue();
            // Descarta las marcaciones del mes siguiente que no cierran un turno de este mes
            if (!ms.isEmpty() && Marcacion.ENTRADA.equals(ms.get(ms.size() - 1).getTipo())
                    && ms.get(ms.size() - 1).getFechaHora().toLocalDate().isAfter(ym.atEndOfMonth())) {
                ms = ms.subList(0, ms.size() - 1);
            }
            List<CalculadoraJornada.Evento> eventos = ms.stream()
                    .map(m -> new CalculadoraJornada.Evento(m.getTipo(), m.getFechaHora())).toList();
            var r = CalculadoraJornada.calcular(eventos, p);
            var dias = r.dias().stream().filter(d -> !d.fecha().isAfter(ym.atEndOfMonth())).toList();
            Empleado e = ms.isEmpty() ? entry.getValue().get(0).getEmpleado() : ms.get(0).getEmpleado();
            BigDecimal h = BigDecimal.ZERO, ed = BigDecimal.ZERO, en = BigDecimal.ZERO, edd = BigDecimal.ZERO;
            int inc = 0;
            for (var d : dias) {
                h = h.add(d.horas());
                ed = ed.add(d.extraDiurna());
                en = en.add(d.extraNocturna());
                edd = edd.add(d.extraDominical());
                if (d.inconsistencia() != null) {
                    inc++;
                }
            }
            int trabajados = (int) dias.stream().filter(d -> d.inconsistencia() == null).map(CalculadoraJornada.Dia::fecha)
                    .distinct().count();
            res.add(new ResumenEmpleado(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), trabajados, h, ed,
                    en, edd, inc, dias));
        }
        res.sort((a, b) -> a.empleado().compareToIgnoreCase(b.empleado()));
        return res;
    }

    /** Pasa las horas extra del mes a las nominas abiertas del periodo. */
    @Transactional
    public ResultadoNomina aplicarANomina(int anio, int mes) {
        int aplicadas = 0, sinNomina = 0, cerradas = 0;
        List<String> detalle = new ArrayList<>();
        for (ResumenEmpleado r : resumen(anio, mes, null)) {
            String estado = nomina.aplicarHorasExtra(r.empleadoId(), anio, mes, r.extraDiurna(), r.extraNocturna(),
                    r.extraDominical());
            switch (estado) {
                case "APLICADA" -> aplicadas++;
                case "CERRADA" -> {
                    cerradas++;
                    detalle.add(r.empleado() + ": la nómina del mes ya está aprobada");
                }
                default -> {
                    sinNomina++;
                    detalle.add(r.empleado() + ": aún no tiene nómina liquidada en el mes");
                }
            }
        }
        audit.registrar("HORAS_EXTRA", "Nómina", null, mes + "/" + anio + ": " + aplicadas + " nóminas actualizadas");
        return new ResultadoNomina(aplicadas, sinNomina, cerradas, detalle);
    }

    // ------------------------------ Utilidades ------------------------------

    private CalculadoraJornada.Parametros parametrosJornada() {
        return new CalculadoraJornada.Parametros(param("HORAS_JORNADA_DIARIA", 8), param("INICIO_NOCTURNO", 19),
                param("FIN_NOCTURNO", 6));
    }

    private int param(String clave, int defecto) {
        try {
            return parametros.decimal(clave).intValue();
        } catch (IllegalStateException e) {
            return defecto;
        }
    }

    public BigDecimal umbral() {
        try {
            return parametros.decimal("UMBRAL_ROSTRO");
        } catch (IllegalStateException e) {
            return new BigDecimal("0.80");
        }
    }

    private void validarPlantilla(String plantilla) {
        try {
            double[] v = json.readValue(plantilla, double[].class);
            if (v.length < 8 || v.length > 4096) {
                throw ApiException.badRequest("La plantilla facial no es válida");
            }
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("La plantilla facial no es válida");
        }
    }

    private static void validarImagen(MultipartFile f, boolean obligatoria) {
        if (f == null || f.isEmpty()) {
            if (obligatoria) {
                throw ApiException.badRequest("Tome la foto del rostro del trabajador");
            }
            return;
        }
        if (!IMAGENES.contains(f.getContentType())) {
            throw ApiException.badRequest("La foto debe ser JPG o PNG");
        }
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("No se pudo leer la foto");
        }
    }
}
