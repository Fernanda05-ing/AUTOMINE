package com.automine.certificados;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.PdfBuilder;
import com.automine.config.AppProperties;
import com.automine.empleados.Empleado;
import com.automine.empleados.HistorialLaboral;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificadoService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Certificado.Repo certificados;
    private final Empleado.Repo empleados;
    private final HistorialLaboral.Repo historial;
    private final AuditService audit;
    private final AppProperties props;

    public record CertificadoResponse(Long id, Long empleadoId, String cedula, String empleado, String codigoValidacion,
            String dirigidoA, boolean incluyeSalario, String estado, String emitidoPor, LocalDateTime fechaEmision,
            String urlValidacion) {
    }

    public record ValidacionResponse(boolean valido, String codigo, String estado, String empleado, String cedula,
            String cargo, LocalDateTime fechaEmision, String empresa) {
    }

    /** RF022: genera el certificado. RN006: solo empleados activos o con historial laboral valido. */
    @Transactional
    public CertificadoResponse generar(Long empleadoId, String dirigidoA, boolean incluyeSalario) {
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            boolean historialValido = e.getFechaRetiro() != null && !e.getFechaRetiro().isBefore(e.getFechaIngreso())
                    && !historial.findByEmpleadoIdOrderByFechaDescIdDesc(empleadoId).isEmpty();
            if (!historialValido) {
                throw ApiException.badRequest("Solo se emiten certificados a empleados activos o con historial "
                        + "laboral válido (RN006)");
            }
        }
        Certificado c = new Certificado();
        c.setEmpleado(e);
        c.setCodigoValidacion(nuevoCodigo());
        c.setDirigidoA(dirigidoA == null || dirigidoA.isBlank() ? "A QUIEN INTERESE" : dirigidoA.trim().toUpperCase());
        c.setIncluyeSalario(incluyeSalario);
        c.setEstado(Certificado.VIGENTE);
        c.setEmitidoPor(AuditService.usuarioActual());
        c.setFechaEmision(LocalDateTime.now());
        certificados.save(c);
        audit.registrar("EMITIR", "Certificado", c.getId(), e.getNombreCompleto() + " " + c.getCodigoValidacion());
        return respuesta(c);
    }

    public Certificado buscar(Long id) {
        return certificados.findWithEmpleadoById(id).orElseThrow(() -> ApiException.notFound("Certificado"));
    }

    public CertificadoResponse obtener(Long id) {
        return respuesta(buscar(id));
    }

    public List<CertificadoResponse> listar(Long empleadoId) {
        return certificados.buscar(empleadoId).stream().map(this::respuesta).toList();
    }

    @Transactional
    public CertificadoResponse anular(Long id) {
        Certificado c = buscar(id);
        c.setEstado(Certificado.ANULADO);
        audit.registrar("ANULAR", "Certificado", id, c.getCodigoValidacion());
        return respuesta(c);
    }

    /** RF024: validacion publica del codigo impreso en el certificado. */
    public ValidacionResponse validar(String codigo) {
        return certificados.findByCodigoValidacion(codigo.trim().toUpperCase())
                .map(c -> {
                    Empleado e = c.getEmpleado();
                    String cedula = e.getCedula();
                    String oculta = cedula.length() > 4 ? "*".repeat(cedula.length() - 4) + cedula.substring(cedula.length() - 4) : cedula;
                    return new ValidacionResponse(Certificado.VIGENTE.equals(c.getEstado()), c.getCodigoValidacion(),
                            c.getEstado(), e.getNombreCompleto(), oculta, e.getCargo(), c.getFechaEmision(),
                            props.company().name());
                })
                .orElse(new ValidacionResponse(false, codigo, "NO_EXISTE", null, null, null, null,
                        props.company().name()));
    }

    /** RF023 / RF025: PDF con firma institucional, codigo de validacion y huella del documento. */
    public byte[] pdf(Long id) {
        Certificado c = buscar(id);
        if (Certificado.ANULADO.equals(c.getEstado())) {
            throw ApiException.badRequest("El certificado fue anulado");
        }
        Empleado e = c.getEmpleado();
        var company = props.company();
        StringBuilder texto = new StringBuilder();
        texto.append("Que el(la) señor(a) ").append(e.getNombreCompleto().toUpperCase())
                .append(", identificado(a) con cédula de ciudadania No. ").append(e.getCedula());
        if (e.isActivo()) {
            texto.append(", labora en nuestra empresa desde el ").append(Fmt.longDate(e.getFechaIngreso()))
                    .append(", desempeñando el cargo de ").append(e.getCargo().toUpperCase())
                    .append(", con contrato a termino ").append(tipoContrato(e.getTipoContrato())).append('.');
        } else {
            texto.append(", laboro en nuestra empresa desde el ").append(Fmt.longDate(e.getFechaIngreso()))
                    .append(" hasta el ").append(Fmt.longDate(e.getFechaRetiro()))
                    .append(", desempeñando como último cargo el de ").append(e.getCargo().toUpperCase()).append('.');
        }
        if (Boolean.TRUE.equals(c.getIncluyeSalario())) {
            texto.append(e.isActivo() ? " Devenga" : " Devengaba").append(" un salario básico mensual de ")
                    .append(Fmt.money(e.getSalario())).append('.');
        }
        LocalDate emision = c.getFechaEmision().toLocalDate();
        String huella = huella(c);
        return new PdfBuilder()
                .header(company.name(), company.nit(), "CERTIFICADO LABORAL")
                .paragraph(c.getDirigidoA())
                .space(4)
                .paragraph("La " + company.signerName() + " de " + company.name() + ", identificada con NIT "
                        + company.nit() + ",")
                .subtitle("CERTIFICA:")
                .paragraph(texto.toString())
                .paragraph("La presente certificación se expide a solicitud del interesado en " + company.city()
                        + ", el " + Fmt.longDate(emision) + ".")
                .signature(company.signerName(), company.signerTitle(), company.name())
                .space(6)
                .keyValues(List.of(
                        new String[] { "Código de validación", c.getCodigoValidacion() },
                        new String[] { "Verificar en", urlValidacion(c.getCodigoValidacion()) },
                        new String[] { "Firma digital institucional", huella }))
                .build();
    }

    private CertificadoResponse respuesta(Certificado c) {
        Empleado e = c.getEmpleado();
        return new CertificadoResponse(c.getId(), e.getId(), e.getCedula(), e.getNombreCompleto(),
                c.getCodigoValidacion(), c.getDirigidoA(), c.getIncluyeSalario(), c.getEstado(), c.getEmitidoPor(),
                c.getFechaEmision(), urlValidacion(c.getCodigoValidacion()));
    }

    private String urlValidacion(String codigo) {
        return props.publicUrl() + "/validar/" + codigo;
    }

    private String nuevoCodigo() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder("AM");
            for (int i = 0; i < 8; i++) {
                sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
            }
            codigo = sb.toString();
        } while (certificados.existsByCodigoValidacion(codigo));
        return codigo;
    }

    /** Huella SHA-256 de los datos certificados firmada con el secreto institucional. */
    private String huella(Certificado c) {
        Empleado e = c.getEmpleado();
        String datos = String.join("|", c.getCodigoValidacion(), e.getCedula(), e.getCargo(),
                e.getSalario().toPlainString(), c.getFechaEmision().toString(), props.jwt().secret());
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 32).toUpperCase();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String tipoContrato(String tipo) {
        return switch (tipo) {
            case "INDEFINIDO" -> "indefinido";
            case "FIJO" -> "fijo";
            case "OBRA_LABOR" -> "de obra o labor";
            case "APRENDIZAJE" -> "de aprendizaje";
            default -> "de prestación de servicios";
        };
    }
}
