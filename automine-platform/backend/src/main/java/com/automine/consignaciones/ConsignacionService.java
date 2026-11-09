package com.automine.consignaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.consignaciones.ConsignacionDtos.ConsignacionRequest;
import com.automine.consignaciones.ConsignacionDtos.ConsignacionResponse;
import com.automine.consignaciones.ConsignacionDtos.LoteResponse;
import com.automine.contabilidad.ContabilidadService;
import com.automine.contabilidad.MovimientoContable;
import com.automine.empleados.Empleado;
import com.automine.nomina.Nomina;
import com.automine.nomina.PeriodoNomina;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsignacionService {

    private static final Set<String> TIPOS = Set.of("application/pdf", "image/jpeg", "image/png");

    private final Consignacion.Repo consignaciones;
    private final Consignacion.Comprobante.Repo comprobantes;
    private final Nomina.Repo nominas;
    private final PeriodoNomina.Repo periodos;
    private final ContabilidadService contabilidad;
    private final AuditService audit;
    private final com.automine.notificaciones.NotificacionService notificaciones;

    /**
     * Servicio RegisterTransfer (RF017, RF018, RF019).
     * RN002: no se permite consignacion sin nomina generada y aprobada.
     */
    @Transactional
    public ConsignacionResponse registrar(ConsignacionRequest req) {
        Nomina n = nominas.findById(req.nominaId())
                .orElseThrow(() -> ApiException.badRequest("No existe la nómina indicada (RN002)"));
        if (!Nomina.APROBADA.equals(n.getEstado())) {
            throw ApiException.badRequest(Nomina.PAGADA.equals(n.getEstado()) ? "La nómina ya está pagada"
                    : "La nómina debe estar aprobada por Contabilidad antes de consignar (RN002)");
        }
        if (consignaciones.existsByReferenciaIgnoreCase(req.referencia().trim())) {
            throw ApiException.conflict("La referencia bancaria " + req.referencia() + " ya fue registrada");
        }
        BigDecimal pendiente = n.getNetoPagar().subtract(consignaciones.totalConsignado(n.getId()));
        BigDecimal valor = req.valor() == null ? pendiente : req.valor();
        if (valor.signum() <= 0) {
            throw ApiException.badRequest("La nómina ya tiene consignaciones por el valor total");
        }
        if (valor.compareTo(pendiente) > 0) {
            throw ApiException.badRequest("El valor supera el saldo pendiente de la nómina (" + Fmt.money(pendiente) + ")");
        }
        Empleado e = n.getEmpleado();
        String banco = blank(req.banco()) != null ? req.banco().trim() : e.getBanco();
        String cuenta = blank(req.cuenta()) != null ? req.cuenta().trim() : e.getNumeroCuenta();
        if (banco == null || cuenta == null) {
            throw ApiException.badRequest("Indique banco y cuenta: el empleado no tiene cuenta registrada");
        }
        Consignacion c = new Consignacion();
        c.setNomina(n);
        c.setBanco(banco);
        c.setCuenta(cuenta);
        c.setValor(valor);
        c.setFechaPago(req.fechaPago());
        c.setReferencia(req.referencia().trim().toUpperCase());
        c.setObservaciones(blank(req.observaciones()));
        c.setEstado(Consignacion.REGISTRADA);
        c.setRegistradoPor(AuditService.usuarioActual());
        consignaciones.save(c);
        audit.registrar("REGISTRAR", "Consignación", c.getId(),
                e.getNombreCompleto() + " " + Fmt.money(valor) + " ref " + c.getReferencia());
        return ConsignacionResponse.of(c);
    }

    /** Registra en bloque las consignaciones de un periodo aprobado usando la cuenta de cada empleado. */
    @Transactional
    public LoteResponse registrarLote(Long periodoId, LocalDate fechaPago, String prefijo) {
        PeriodoNomina p = periodos.findById(periodoId).orElseThrow(() -> ApiException.notFound("Periodo"));
        if (!PeriodoNomina.APROBADO.equals(p.getEstado())) {
            throw ApiException.badRequest("El periodo debe estar aprobado para consignar (RN002)");
        }
        String pre = blank(prefijo) == null ? "NOM" : prefijo.trim().toUpperCase();
        int registradas = 0;
        int sinCuenta = 0;
        int ya = 0;
        for (Nomina n : nominas.findByPeriodoIdOrderByEmpleadoApellidosAsc(periodoId)) {
            BigDecimal pendiente = n.getNetoPagar().subtract(consignaciones.totalConsignado(n.getId()));
            if (!Nomina.APROBADA.equals(n.getEstado()) || pendiente.signum() <= 0) {
                ya++;
                continue;
            }
            Empleado e = n.getEmpleado();
            if (e.getBanco() == null || e.getNumeroCuenta() == null) {
                sinCuenta++;
                continue;
            }
            String ref = String.format("%s-%04d%02d-%s", pre, p.getAnio(), p.getMes(), e.getCedula());
            if (consignaciones.existsByReferenciaIgnoreCase(ref)) {
                ref = ref + "-" + n.getId();
            }
            Consignacion c = new Consignacion();
            c.setNomina(n);
            c.setBanco(e.getBanco());
            c.setCuenta(e.getNumeroCuenta());
            c.setValor(pendiente);
            c.setFechaPago(fechaPago);
            c.setReferencia(ref);
            c.setEstado(Consignacion.REGISTRADA);
            c.setRegistradoPor(AuditService.usuarioActual());
            consignaciones.save(c);
            registradas++;
        }
        audit.registrar("REGISTRAR_LOTE", "Consignación", periodoId, registradas + " consignaciones");
        return new LoteResponse(registradas, sinCuenta, ya);
    }

    /**
     * RN005: solo Contabilidad aprueba pagos. Al aprobar se registra el gasto de nomina
     * y, si la nomina queda cubierta, pasa a PAGADA.
     */
    @Transactional
    public ConsignacionResponse aprobar(Long id) {
        Consignacion c = buscar(id);
        if (!Consignacion.REGISTRADA.equals(c.getEstado())) {
            throw ApiException.badRequest("Solo se pueden aprobar consignaciones en estado REGISTRADA");
        }
        c.setEstado(Consignacion.APROBADA);
        c.setAprobadoPor(AuditService.usuarioActual());
        c.setFechaAprobacion(LocalDateTime.now());
        Nomina n = c.getNomina();
        consignaciones.flush();
        if (consignaciones.totalAprobado(n.getId()).compareTo(n.getNetoPagar()) >= 0) {
            n.setEstado(Nomina.PAGADA);
            PeriodoNomina p = n.getPeriodo();
            nominas.flush();
            if (nominas.countByPeriodoIdAndEstadoNot(p.getId(), Nomina.PAGADA) == 0) {
                p.setEstado(PeriodoNomina.PAGADO);
            }
        }
        contabilidad.registrarAutomatico(MovimientoContable.GASTO, c.getFechaPago(),
                "Pago nómina " + Fmt.mes(n.getPeriodo().getMes()) + " " + n.getPeriodo().getAnio() + " - "
                        + n.getEmpleado().getNombreCompleto(),
                "Nómina", c.getValor(), c.getReferencia(), MovimientoContable.ORIGEN_NOMINA);
        audit.registrar("APROBAR", "Consignación", id, c.getReferencia());
        notificaciones.pagoAprobado(n.getEmpleado(), c.getValor(), c.getBanco(), ConsignacionDtos.ConsignacionResponse.enmascarar(c.getCuenta()),
                c.getFechaPago(), c.getReferencia());
        return ConsignacionResponse.of(c);
    }

    @Transactional
    public ConsignacionResponse anular(Long id, String motivo) {
        Consignacion c = buscar(id);
        if (!Consignacion.REGISTRADA.equals(c.getEstado())) {
            throw ApiException.badRequest("Solo se pueden anular consignaciones que no han sido aprobadas");
        }
        c.setEstado(Consignacion.ANULADA);
        c.setObservaciones(("ANULADA: " + motivo).length() > 255 ? ("ANULADA: " + motivo).substring(0, 255)
                : "ANULADA: " + motivo);
        audit.registrar("ANULAR", "Consignación", id, motivo);
        return ConsignacionResponse.of(c);
    }

    /** RF020: subir comprobante (PDF o imagen). */
    @Transactional
    public ConsignacionResponse subirComprobante(Long id, MultipartFile file) {
        Consignacion c = buscar(id);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Debe adjuntar el comprobante");
        }
        if (!TIPOS.contains(file.getContentType())) {
            throw ApiException.badRequest("El comprobante debe ser PDF, JPG o PNG");
        }
        Consignacion.Comprobante comp = comprobantes.findByConsignacionId(id).orElseGet(Consignacion.Comprobante::new);
        comp.setConsignacionId(id);
        comp.setNombre(file.getOriginalFilename() == null ? "comprobante" : file.getOriginalFilename());
        comp.setContentType(file.getContentType());
        try {
            comp.setDatos(file.getBytes());
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
        comprobantes.save(comp);
        c.setComprobanteNombre(comp.getNombre());
        audit.registrar("COMPROBANTE", "Consignación", id, comp.getNombre());
        return ConsignacionResponse.of(c);
    }

    public Consignacion.Comprobante comprobante(Long id) {
        Consignacion.Comprobante comp = comprobantes.findByConsignacionId(id)
                .orElseThrow(() -> ApiException.notFound("Comprobante"));
        comp.getDatos();
        return comp;
    }

    public List<ConsignacionResponse> listar(Long periodoId, String estado) {
        return consignaciones.buscar(periodoId, blank(estado)).stream().map(ConsignacionResponse::of).toList();
    }

    /** RF021 / Servicio ListTransfers. */
    public List<ConsignacionResponse> delEmpleado(Long empleadoId) {
        return consignaciones.delEmpleado(empleadoId).stream().map(ConsignacionResponse::of).toList();
    }

    public Consignacion buscar(Long id) {
        return consignaciones.findWithNominaById(id).orElseThrow(() -> ApiException.notFound("Consignación"));
    }

    public ConsignacionResponse obtener(Long id) {
        return ConsignacionResponse.of(buscar(id));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
