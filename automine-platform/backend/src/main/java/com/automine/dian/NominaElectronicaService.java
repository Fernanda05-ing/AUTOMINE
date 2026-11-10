package com.automine.dian;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.ParametroService;
import com.automine.config.AppProperties;
import com.automine.empleados.Empleado;
import com.automine.incapacidades.CalculadoraIncapacidad;
import com.automine.incapacidades.Incapacidad;
import com.automine.nomina.Nomina;
import com.automine.nomina.PeriodoNomina;
import com.automine.prestaciones.CalculadoraPrestaciones;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Documento soporte de pago de nomina electronica (NominaIndividual, TipoXML 102) con la estructura
 * del anexo tecnico de la DIAN (Resolucion 000013 de 2021). Se genera para revision o para entregar
 * a un proveedor tecnologico: NO se firma ni se envia a la DIAN. El CUNE se calcula con el PIN del
 * software configurado y el ambiente 2 (pruebas) salvo que se configure otro.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NominaElectronicaService {

    private static final String NS = "urn:dian:gov:co:facturaelectronica:NominaIndividual";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Nomina.Repo nominas;
    private final PeriodoNomina.Repo periodos;
    private final ParametroService parametros;
    private final AppProperties props;
    private final EntityManager em;

    @Value("${app.dian.prefijo:NE}")
    private String prefijo;
    @Value("${app.dian.software-pin:00000}")
    private String softwarePin;
    @Value("${app.dian.software-id:}")
    private String softwareId;
    @Value("${app.dian.ambiente:2}")
    private String ambiente;
    @Value("${app.dian.departamento:05}")
    private String departamento;
    @Value("${app.dian.municipio:05001}")
    private String municipio;
    @Value("${app.dian.direccion:Sin dirección}")
    private String direccion;

    public record Documento(String nombre, String xml, String cune) {
    }

    public Documento generar(Long nominaId) {
        Nomina n = nominas.findById(nominaId).orElseThrow(() -> ApiException.notFound("Nómina"));
        return generar(n);
    }

    public byte[] zipPeriodo(Long periodoId) {
        PeriodoNomina p = periodos.findById(periodoId).orElseThrow(() -> ApiException.notFound("Periodo"));
        List<Nomina> lista = nominas.findByPeriodoIdOrderByEmpleadoApellidosAsc(p.getId());
        if (lista.isEmpty()) {
            throw ApiException.badRequest("El periodo no tiene liquidaciones");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Nomina n : lista) {
                Documento d = generar(n);
                zip.putNextEntry(new ZipEntry(d.nombre()));
                zip.write(d.xml().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    Documento generar(Nomina n) {
        if (Nomina.PENDIENTE.equals(n.getEstado())) {
            throw ApiException.badRequest("La nómina debe estar aprobada para generar el documento electrónico");
        }
        Empleado e = n.getEmpleado();
        PeriodoNomina p = n.getPeriodo();
        YearMonth ym = YearMonth.of(p.getAnio(), p.getMes());
        LocalDate ini = ym.atDay(1);
        LocalDate fin = ym.atEndOfMonth();
        LocalDate liqIni = e.getFechaIngreso().isAfter(ini) ? e.getFechaIngreso() : ini;
        LocalDate liqFin = e.getFechaRetiro() != null && e.getFechaRetiro().isBefore(fin) ? e.getFechaRetiro() : fin;
        LocalDateTime gen = LocalDateTime.now();
        String fecGen = gen.toLocalDate().toString();
        String horGen = gen.toLocalTime().format(HORA) + "-05:00";
        String numero = prefijo + n.getId();

        // ---------------- Devengados ----------------
        BigDecimal valorHora = n.getSalarioBase().divide(parametros.decimal("HORAS_MES", fin), 6, RoundingMode.HALF_UP);
        Map<String, BigDecimal> prestaciones = prestacionesDelMes(e.getId(), ini, fin);
        BigDecimal prima = prestaciones.getOrDefault("PRIMA", BigDecimal.ZERO);
        BigDecimal intereses = prestaciones.getOrDefault("INTERESES", BigDecimal.ZERO);
        BigDecimal cesantias = prestaciones.getOrDefault("CESANTIAS", BigDecimal.ZERO);

        StringBuilder dev = new StringBuilder();
        dev.append(el("Basico", "DiasTrabajados", String.valueOf(n.getDiasTrabajados()), "SueldoTrabajado", m(n.getSalarioDevengado())));
        if (pos(n.getAuxilioTransporte())) {
            dev.append(el("Transporte", "AuxilioTransporte", m(n.getAuxilioTransporte())));
        }
        extra(dev, "HEDs", "HED", n.getHorasExtraDiurnas(), parametros.decimal("RECARGO_EXTRA_DIURNA", fin), valorHora);
        extra(dev, "HENs", "HEN", n.getHorasExtraNocturnas(), parametros.decimal("RECARGO_EXTRA_NOCTURNA", fin), valorHora);
        extra(dev, "HEDDFs", "HEDDF", n.getHorasExtraDominicales(), parametros.decimal("RECARGO_EXTRA_DOMINICAL", fin), valorHora);
        if (n.getDiasVacaciones() != null && n.getDiasVacaciones() > 0) {
            dev.append("<Vacaciones>")
                    .append(el("VacacionesComunes", "Cantidad", String.valueOf(n.getDiasVacaciones()), "Pago", m(n.getValorVacaciones())))
                    .append("</Vacaciones>");
        }
        if (pos(prima)) {
            dev.append(el("Primas", "Cantidad", "180", "Pago", m(prima)));
        }
        if (pos(cesantias) || pos(intereses)) {
            dev.append(el("Cesantias", "Pago", m(cesantias), "Porcentaje", "12.00", "PagoIntereses", m(intereses)));
        }
        String incap = incapacidades(n, ym);
        if (!incap.isEmpty()) {
            dev.append("<Incapacidades>").append(incap).append("</Incapacidades>");
        }
        if (pos(n.getBonificaciones())) {
            dev.append("<Bonificaciones>").append(el("Bonificacion", "BonificacionS", m(n.getBonificaciones()))).append("</Bonificaciones>");
        }
        BigDecimal totalDev = n.getTotalDevengado().add(prima).add(intereses).add(cesantias);

        // ---------------- Deducciones ----------------
        StringBuilder ded = new StringBuilder();
        ded.append(el("Salud", "Porcentaje", pct(parametros.decimal("PORC_SALUD", fin)), "Deduccion", m(n.getSalud())));
        ded.append(el("FondoPension", "Porcentaje", pct(parametros.decimal("PORC_PENSION", fin)), "Deduccion", m(n.getPension())));
        if (pos(n.getFondoSolidaridad())) {
            ded.append(el("FondoSP", "Porcentaje", "1.00", "DeduccionSP", m(n.getFondoSolidaridad())));
        }
        if (pos(n.getOtrosDescuentos())) {
            ded.append("<OtrasDeducciones><OtraDeduccion>").append(m(n.getOtrosDescuentos())).append("</OtraDeduccion></OtrasDeducciones>");
        }
        BigDecimal totalDed = n.getTotalDescuentos();
        BigDecimal neto = totalDev.subtract(totalDed);

        // ---------------- CUNE ----------------
        String[] nit = nit(props.company().nit());
        String cune = sha384(numero + fecGen + horGen + m(totalDev) + m(totalDed) + m(neto) + nit[0] + e.getCedula() + "102"
                + softwarePin + ambiente);
        String qr = "https://catalogo-vpfe" + ("1".equals(ambiente) ? "" : "-hab") + ".dian.gov.co/document/searchqr?documentkey=" + cune;

        String[] apellidos = partir(e.getApellidos());
        String[] nombres = partir(e.getNombres());
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        x.append("<NominaIndividual xmlns=\"").append(NS).append("\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"")
                .append(" xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\"")
                .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                .append(" SchemaLocation=\"\" xsi:schemaLocation=\"").append(NS).append(" NominaIndividualElectronicaXSD.xsd\">\n");
        x.append("  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent><!-- Firma digital del proveedor tecnológico --></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>\n");
        x.append("  ").append(el("Periodo", "FechaIngreso", e.getFechaIngreso().toString(),
                "FechaRetiro", e.getFechaRetiro() == null ? null : e.getFechaRetiro().toString(),
                "FechaLiquidacionInicio", liqIni.toString(), "FechaLiquidacionFin", liqFin.toString(),
                "TiempoLaborado", String.valueOf(CalculadoraPrestaciones.dias360(e.getFechaIngreso(), liqFin)),
                "FechaGen", fecGen)).append('\n');
        x.append("  ").append(el("NumeroSecuenciaXML", "CodigoTrabajador", e.getCedula(), "Prefijo", prefijo,
                "Consecutivo", String.valueOf(n.getId()), "Numero", numero)).append('\n');
        x.append("  ").append(el("LugarGeneracionXML", "Pais", "CO", "DepartamentoEstado", departamento,
                "MunicipioCiudad", municipio, "Idioma", "es")).append('\n');
        x.append("  ").append(el("ProveedorXML", "RazonSocial", props.company().name(), "NIT", nit[0], "DV", nit[1],
                "SoftwareID", softwareId, "SoftwareSC", sha384(softwareId + softwarePin + numero))).append('\n');
        x.append("  <CodigoQR>").append(esc(qr)).append("</CodigoQR>\n");
        x.append("  ").append(el("InformacionGeneral", "Version", "V1.0: Documento Soporte de Pago de Nómina Electrónica",
                "Ambiente", ambiente, "TipoXML", "102", "CUNE", cune, "EncripCUNE", "CUNE-SHA384", "FechaGen", fecGen,
                "HoraGen", horGen, "PeriodoNomina", "5", "TipoMoneda", "COP")).append('\n');
        x.append("  <Notas>Generado por AUTOMINE. Sin firma digital: no enviado a la DIAN.</Notas>\n");
        x.append("  ").append(el("Empleador", "RazonSocial", props.company().name(), "NIT", nit[0], "DV", nit[1],
                "Pais", "CO", "DepartamentoEstado", departamento, "MunicipioCiudad", municipio, "Direccion", direccion)).append('\n');
        x.append("  ").append(el("Trabajador", "TipoTrabajador", "APRENDIZAJE".equals(e.getTipoContrato()) ? "12" : "01",
                "SubTipoTrabajador", "00", "AltoRiesgoPension", "false", "TipoDocumento", "13", "NumeroDocumento", e.getCedula(),
                "PrimerApellido", apellidos[0], "SegundoApellido", apellidos[1], "PrimerNombre", nombres[0],
                "OtrosNombres", nombres[1], "LugarTrabajoPais", "CO", "LugarTrabajoDepartamentoEstado", departamento,
                "LugarTrabajoMunicipioCiudad", municipio, "LugarTrabajoDireccion", direccion, "SalarioIntegral", "false",
                "TipoContrato", tipoContrato(e.getTipoContrato()), "Sueldo", m(n.getSalarioBase()), "CodigoTrabajador", e.getCedula()))
                .append('\n');
        x.append("  ").append(el("Pago", "Forma", "1", "Metodo", e.getNumeroCuenta() == null ? "10" : "42",
                "Banco", e.getBanco(), "TipoCuenta", e.getTipoCuenta(), "NumeroCuenta", e.getNumeroCuenta())).append('\n');
        x.append("  <FechasPagos><FechaPago>").append(fin).append("</FechaPago></FechasPagos>\n");
        x.append("  <Devengados>").append(dev).append("</Devengados>\n");
        x.append("  <Deducciones>").append(ded).append("</Deducciones>\n");
        x.append("  <DevengadosTotal>").append(m(totalDev)).append("</DevengadosTotal>\n");
        x.append("  <DeduccionesTotal>").append(m(totalDed)).append("</DeduccionesTotal>\n");
        x.append("  <ComprobanteTotal>").append(m(neto)).append("</ComprobanteTotal>\n");
        x.append("</NominaIndividual>\n");
        return new Documento(numero + "_" + e.getCedula() + "_" + p.getAnio() + String.format("%02d", p.getMes()) + ".xml",
                x.toString(), cune);
    }

    // ------------------------------ Partes ------------------------------

    private static void extra(StringBuilder dev, String grupo, String tag, BigDecimal horas, BigDecimal factor, BigDecimal valorHora) {
        if (!pos(horas)) {
            return;
        }
        BigDecimal pago = valorHora.multiply(horas).multiply(factor).setScale(0, RoundingMode.HALF_UP);
        String porcentaje = factor.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
        dev.append('<').append(grupo).append('>')
                .append(el(tag, "Cantidad", horas.stripTrailingZeros().toPlainString(), "Porcentaje", porcentaje, "Pago", m(pago)))
                .append("</").append(grupo).append('>');
    }

    /** Incapacidades del mes separadas por tipo DIAN (1 comun, 2 profesional, 3 laboral). */
    private String incapacidades(Nomina n, YearMonth ym) {
        if (n.getDiasIncapacidad() == null || n.getDiasIncapacidad() == 0) {
            return "";
        }
        List<Incapacidad> lista = em.createQuery("""
                select i from Incapacidad i where i.empleado.id = :e and i.estado = 'APROBADA'
                  and i.fechaInicio <= :fin and i.fechaFin >= :ini order by i.fechaInicio""", Incapacidad.class)
                .setParameter("e", n.getEmpleado().getId()).setParameter("ini", ym.atDay(1)).setParameter("fin", ym.atEndOfMonth())
                .getResultList();
        BigDecimal minimo = parametros.decimal("SALARIO_MINIMO", ym.atEndOfMonth());
        StringBuilder sb = new StringBuilder();
        for (Incapacidad i : lista) {
            if (i.getTipo().startsWith("LICENCIA")) {
                continue;
            }
            var r = CalculadoraIncapacidad.delMes(List.of(new CalculadoraIncapacidad.Tramo(i.getTipo(), i.getFechaInicio(),
                    i.getFechaFin(), 0)), ym, n.getSalarioBase(), minimo);
            String tipo = switch (i.getTipo()) {
                case "ENFERMEDAD_LABORAL" -> "2";
                case "ACCIDENTE_TRABAJO" -> "3";
                default -> "1";
            };
            LocalDate d = i.getFechaInicio().isBefore(ym.atDay(1)) ? ym.atDay(1) : i.getFechaInicio();
            LocalDate h = i.getFechaFin().isAfter(ym.atEndOfMonth()) ? ym.atEndOfMonth() : i.getFechaFin();
            sb.append(el("Incapacidad", "FechaInicio", d.toString(), "FechaFin", h.toString(), "Cantidad",
                    String.valueOf(r.dias()), "Tipo", tipo, "Pago", m(r.valor())));
        }
        return sb.toString();
    }

    private Map<String, BigDecimal> prestacionesDelMes(Long empleadoId, LocalDate ini, LocalDate fin) {
        List<Object[]> filas = em.createQuery("""
                select p.tipo, sum(p.valor) from PagoPrestacion p
                where p.empleado.id = :e and p.createdAt >= :ini and p.createdAt < :fin group by p.tipo""", Object[].class)
                .setParameter("e", empleadoId).setParameter("ini", ini.atStartOfDay()).setParameter("fin", fin.plusDays(1).atStartOfDay())
                .getResultList();
        Map<String, BigDecimal> res = new LinkedHashMap<>();
        filas.forEach(f -> res.put((String) f[0], (BigDecimal) f[1]));
        return res;
    }

    static String tipoContrato(String t) {
        return switch (t) {
            case "FIJO" -> "1";
            case "OBRA_LABOR" -> "3";
            case "APRENDIZAJE" -> "4";
            default -> "2";
        };
    }

    /** NIT y digito de verificacion (modulo 11 de la DIAN). */
    public static String[] nit(String valor) {
        String v = valor == null ? "" : valor.trim();
        String[] partes = v.split("-");
        String num = partes[0].replaceAll("[^0-9]", "");
        if (num.isEmpty()) {
            return new String[] { "0", "0" };
        }
        if (partes.length > 1 && !partes[1].isBlank()) {
            return new String[] { num, partes[1].replaceAll("[^0-9]", "") };
        }
        int[] pesos = { 3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71 };
        int suma = 0;
        for (int i = 0; i < num.length() && i < pesos.length; i++) {
            suma += (num.charAt(num.length() - 1 - i) - '0') * pesos[i];
        }
        int r = suma % 11;
        return new String[] { num, String.valueOf(r > 1 ? 11 - r : r) };
    }

    static String sha384(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String[] partir(String s) {
        String[] p = (s == null ? "" : s.trim()).split("\\s+", 2);
        return new String[] { p[0], p.length > 1 ? p[1] : null };
    }

    private static boolean pos(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static String m(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String pct(BigDecimal fraccion) {
        return fraccion.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Elemento vacio con atributos (pares nombre, valor); omite los valores nulos o vacios. */
    private static String el(String nombre, String... kv) {
        StringBuilder sb = new StringBuilder("<").append(nombre);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null && !kv[i + 1].isBlank()) {
                sb.append(' ').append(kv[i]).append("=\"").append(esc(kv[i + 1])).append('"');
            }
        }
        return sb.append("/>").toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
