package com.automine.empleados;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.Roles;
import com.automine.empleados.EmpleadoDtos.ContratoRequest;
import com.automine.empleados.EmpleadoDtos.EmpleadoOpcion;
import com.automine.empleados.EmpleadoDtos.EmpleadoRequest;
import com.automine.empleados.EmpleadoDtos.EmpleadoResponse;
import com.automine.empleados.EmpleadoDtos.InactivarRequest;
import com.automine.empleados.EmpleadoDtos.NovedadRequest;
import com.automine.empleados.EmpleadoDtos.ResumenEmpleados;
import com.automine.security.RolRepository;
import com.automine.security.Usuario;
import com.automine.security.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmpleadoService {

    private static final Set<String> TIPOS_DOCUMENTO = Set.of("application/pdf", "image/jpeg", "image/png",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Empleado.Repo empleados;
    private final HistorialLaboral.Repo historial;
    private final Contrato.Repo contratos;
    private final DocumentoEmpleado.Repo documentos;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final com.automine.asistencia.Rostro.Repo rostros;
    private final PasswordEncoder encoder;
    private final com.automine.notificaciones.NotificacionService notificaciones;
    private final AuditService audit;

    public Empleado buscar(Long id) {
        return empleados.findById(id).orElseThrow(() -> ApiException.notFound("Empleado"));
    }

    public List<EmpleadoResponse> listar(String q, String estado) {
        String query = q == null || q.isBlank() ? null : q.trim();
        String est = estado == null || estado.isBlank() ? null : estado;
        return empleados.buscar(query, est).stream().map(EmpleadoResponse::of).toList();
    }

    public List<EmpleadoOpcion> opciones(boolean soloActivos) {
        List<Empleado> lista = soloActivos ? empleados.findByEstadoOrderByApellidosAsc(Empleado.ACTIVO)
                : empleados.findAllByOrderByApellidosAsc();
        return lista.stream().map(EmpleadoOpcion::of).toList();
    }

    public EmpleadoResponse obtener(Long id) {
        Empleado e = buscar(id);
        Long usuarioId = usuarios.findFirstByEmpleadoId(id).map(Usuario::getId).orElse(null);
        return EmpleadoResponse.of(e, usuarioId, null);
    }

    public ResumenEmpleados resumen() {
        long activos = empleados.countByEstado(Empleado.ACTIVO);
        long inactivos = empleados.countByEstado(Empleado.INACTIVO);
        return new ResumenEmpleados(activos + inactivos, activos, inactivos);
    }

    /** RF006 / Servicio CreateEmployee. RN003: cedula unica. RN004: solo RRHH (y administrador). */
    @Transactional
    public EmpleadoResponse crear(EmpleadoRequest req) {
        if (empleados.existsByCedula(req.cedula())) {
            throw ApiException.conflict("Ya existe un empleado con la cédula " + req.cedula());
        }
        Empleado e = new Empleado();
        aplicar(e, req);
        e.setEstado(Empleado.ACTIVO);
        empleados.save(e);

        Contrato c = new Contrato();
        c.setEmpleadoId(e.getId());
        c.setTipo(req.tipoContrato());
        c.setCargo(req.cargo());
        c.setSalario(req.salario());
        c.setFechaInicio(req.fechaIngreso());
        c.setEstado(Contrato.VIGENTE);
        contratos.save(c);

        agregarHistorial(e.getId(), req.fechaIngreso(), "INGRESO",
                "Ingreso como " + req.cargo() + " con salario " + Fmt.money(req.salario()));

        Long usuarioId = null;
        String temporal = null;
        if (Boolean.TRUE.equals(req.crearUsuario())) {
            if (e.getEmail() == null || e.getEmail().isBlank()) {
                throw ApiException.badRequest("Para crear el usuario de la app el empleado debe tener correo");
            }
            if (usuarios.existsByEmailIgnoreCase(e.getEmail())) {
                throw ApiException.conflict("Ya existe un usuario con el correo " + e.getEmail());
            }
            temporal = passwordTemporal();
            Usuario u = new Usuario();
            u.setNombre(e.getNombreCompleto());
            u.setEmail(e.getEmail().toLowerCase());
            u.setPasswordHash(encoder.encode(temporal));
            u.setRol(roles.findByCodigo(Roles.EMPLEADO).orElseThrow());
            u.setEmpleadoId(e.getId());
            u.setEstado(Usuario.ACTIVO);
            u.setDebeCambiarPassword(true);
            usuarios.save(u);
            usuarioId = u.getId();
            notificaciones.bienvenida(u.getEmail(), e.getNombreCompleto(), temporal);
        }
        audit.registrar("CREAR", "Empleado", e.getId(), e.getCedula() + " " + e.getNombreCompleto());
        return EmpleadoResponse.of(e, usuarioId, temporal);
    }

    /** RF007: editar informacion laboral. Los cambios de cargo y salario quedan en el historial. */
    @Transactional
    public EmpleadoResponse editar(Long id, EmpleadoRequest req) {
        Empleado e = buscar(id);
        if (!e.getCedula().equals(req.cedula()) && empleados.existsByCedula(req.cedula())) {
            throw ApiException.conflict("Ya existe un empleado con la cédula " + req.cedula());
        }
        String cargoAnterior = e.getCargo();
        BigDecimal salarioAnterior = e.getSalario();
        aplicar(e, req);
        LocalDate hoy = LocalDate.now();
        if (!Objects.equals(cargoAnterior, req.cargo())) {
            agregarHistorial(id, hoy, "CAMBIO_CARGO", "Cambio de cargo: " + cargoAnterior + " -> " + req.cargo());
        }
        if (salarioAnterior.compareTo(req.salario()) != 0) {
            agregarHistorial(id, hoy, "CAMBIO_SALARIO",
                    "Cambio de salario: " + Fmt.money(salarioAnterior) + " -> " + Fmt.money(req.salario()));
        }
        audit.registrar("EDITAR", "Empleado", id, e.getNombreCompleto());
        return obtener(id);
    }

    /** RF008: inactivar empleado (retiro). Tambien bloquea su usuario de la app. */
    @Transactional
    public EmpleadoResponse inactivar(Long id, InactivarRequest req) {
        Empleado e = buscar(id);
        if (!e.isActivo()) {
            throw ApiException.badRequest("El empleado ya está inactivo");
        }
        if (req.fechaRetiro().isBefore(e.getFechaIngreso())) {
            throw ApiException.badRequest("La fecha de retiro no puede ser anterior a la de ingreso");
        }
        e.setEstado(Empleado.INACTIVO);
        e.setFechaRetiro(req.fechaRetiro());
        contratos.findByEmpleadoIdAndEstado(id, Contrato.VIGENTE).forEach(c -> {
            c.setEstado(Contrato.TERMINADO);
            c.setFechaFin(req.fechaRetiro());
        });
        usuarios.findFirstByEmpleadoId(id).ifPresent(u -> u.setEstado(Usuario.INACTIVO));
        // Ley 1581: el dato biometrico solo se conserva mientras dure la relacion laboral
        rostros.findByEmpleadoId(id).ifPresent(r -> com.automine.common.ArchivoStore.borrar(r.getFoto()));
        rostros.borrarDeEmpleado(id);
        agregarHistorial(id, req.fechaRetiro(), "RETIRO", req.motivo());
        audit.registrar("INACTIVAR", "Empleado", id, req.motivo());
        return obtener(id);
    }

    @Transactional
    public EmpleadoResponse reactivar(Long id) {
        Empleado e = buscar(id);
        if (e.isActivo()) {
            throw ApiException.badRequest("El empleado ya está activo");
        }
        e.setEstado(Empleado.ACTIVO);
        e.setFechaRetiro(null);
        usuarios.findFirstByEmpleadoId(id).ifPresent(u -> u.setEstado(Usuario.ACTIVO));
        agregarHistorial(id, LocalDate.now(), "REINTEGRO", "Reintegro del empleado");
        audit.registrar("REACTIVAR", "Empleado", id, null);
        return obtener(id);
    }

    public List<HistorialLaboral> historial(Long id) {
        buscar(id);
        return historial.findByEmpleadoIdOrderByFechaDescIdDesc(id);
    }

    @Transactional
    public HistorialLaboral novedad(Long id, NovedadRequest req) {
        buscar(id);
        audit.registrar("NOVEDAD", "Empleado", id, req.tipo());
        return agregarHistorial(id, req.fecha(), req.tipo(), req.descripcion());
    }

    public List<Contrato> contratos(Long id) {
        buscar(id);
        return contratos.findByEmpleadoIdOrderByFechaInicioDesc(id);
    }

    /** Nuevo contrato u otrosi: termina el vigente y actualiza cargo y salario del empleado. */
    @Transactional
    public Contrato nuevoContrato(Long id, ContratoRequest req) {
        Empleado e = buscar(id);
        if (!e.isActivo()) {
            throw ApiException.badRequest("No se pueden registrar contratos a un empleado inactivo");
        }
        if (req.fechaFin() != null && req.fechaFin().isBefore(req.fechaInicio())) {
            throw ApiException.badRequest("La fecha final debe ser posterior a la inicial");
        }
        contratos.findByEmpleadoIdAndEstado(id, Contrato.VIGENTE).forEach(c -> {
            c.setEstado(Contrato.TERMINADO);
            c.setFechaFin(req.fechaInicio().minusDays(1));
        });
        Contrato c = new Contrato();
        c.setEmpleadoId(id);
        c.setTipo(req.tipo());
        c.setCargo(req.cargo());
        c.setSalario(req.salario());
        c.setFechaInicio(req.fechaInicio());
        c.setFechaFin(req.fechaFin());
        c.setEstado(Contrato.VIGENTE);
        contratos.save(c);
        e.setTipoContrato(req.tipo());
        e.setCargo(req.cargo());
        e.setSalario(req.salario());
        agregarHistorial(id, req.fechaInicio(), "CONTRATO",
                "Nuevo contrato " + req.tipo() + " como " + req.cargo() + " por " + Fmt.money(req.salario()));
        audit.registrar("CONTRATO", "Empleado", id, req.tipo());
        return c;
    }

    public List<DocumentoEmpleado.Resumen> documentos(Long id) {
        buscar(id);
        return documentos.listar(id);
    }

    @Transactional
    public DocumentoEmpleado.Resumen subirDocumento(Long id, String tipo, MultipartFile file) {
        buscar(id);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Debe adjuntar un archivo");
        }
        if (!TIPOS_DOCUMENTO.contains(file.getContentType())) {
            throw ApiException.badRequest("Formato no permitido. Use PDF, JPG, PNG o Word");
        }
        DocumentoEmpleado d = new DocumentoEmpleado();
        d.setEmpleadoId(id);
        d.setNombre(file.getOriginalFilename() == null ? "documento" : file.getOriginalFilename());
        d.setTipo(tipo == null || tipo.isBlank() ? "OTRO" : tipo.toUpperCase());
        d.setContentType(file.getContentType());
        d.setTamano(file.getSize());
        try {
            d.setDatos(file.getBytes());
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
        d.setSubidoPor(AuditService.usuarioActual());
        documentos.save(d);
        audit.registrar("ADJUNTAR", "Empleado", id, d.getNombre());
        return new DocumentoEmpleado.Resumen(d.getId(), id, d.getNombre(), d.getTipo(), d.getContentType(),
                d.getTamano(), d.getSubidoPor(), d.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public DocumentoEmpleado documento(Long id, Long documentoId) {
        DocumentoEmpleado d = documentos.findById(documentoId).orElseThrow(() -> ApiException.notFound("Documento"));
        if (!d.getEmpleadoId().equals(id)) {
            throw ApiException.notFound("Documento");
        }
        d.getDatos();
        return d;
    }

    @Transactional
    public void eliminarDocumento(Long id, Long documentoId) {
        DocumentoEmpleado d = documento(id, documentoId);
        com.automine.common.ArchivoStore.borrar(d.getDatos());
        documentos.delete(d);
        audit.registrar("ELIMINAR_DOCUMENTO", "Empleado", id, d.getNombre());
    }

    private HistorialLaboral agregarHistorial(Long empleadoId, LocalDate fecha, String tipo, String descripcion) {
        HistorialLaboral h = new HistorialLaboral();
        h.setEmpleadoId(empleadoId);
        h.setFecha(fecha);
        h.setTipo(tipo);
        h.setDescripcion(descripcion);
        h.setRegistradoPor(AuditService.usuarioActual());
        return historial.save(h);
    }

    private void aplicar(Empleado e, EmpleadoRequest r) {
        e.setCedula(r.cedula().trim());
        e.setNombres(r.nombres().trim());
        e.setApellidos(r.apellidos().trim());
        e.setEmail(blank(r.email()) == null ? null : r.email().trim().toLowerCase());
        e.setTelefono(celular(r.telefono()));
        e.setDireccion(blank(r.direccion()));
        e.setFechaNacimiento(r.fechaNacimiento());
        e.setCargo(r.cargo().trim());
        e.setArea(blank(r.area()));
        e.setSalario(r.salario());
        e.setFechaIngreso(r.fechaIngreso());
        e.setTipoContrato(r.tipoContrato());
        e.setBanco(blank(r.banco()));
        e.setTipoCuenta(blank(r.tipoCuenta()));
        e.setNumeroCuenta(blank(r.numeroCuenta()));
        e.setEps(blank(r.eps()));
        e.setFondoPension(blank(r.fondoPension()));
        e.setArl(blank(r.arl()));
    }

    /** Guarda el celular como 10 digitos (sin +57, espacios ni guiones) para usarlo con WhatsApp. */
    public static String celular(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String d = s.replaceAll("[^0-9]", "");
        return d.length() == 12 && d.startsWith("57") ? d.substring(2) : d;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String passwordTemporal() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
        StringBuilder sb = new StringBuilder("Am");
        for (int i = 0; i < 6; i++) {
            sb.append(letras.charAt(RANDOM.nextInt(letras.length())));
        }
        sb.append(RANDOM.nextInt(90) + 10).append('*');
        return sb.toString();
    }
}
