package com.automine.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.ParametroService;
import com.automine.common.PdfBuilder;
import com.automine.config.AppProperties;
import com.automine.empleados.Empleado;
import com.automine.incapacidades.CalculadoraIncapacidad;
import com.automine.incapacidades.Incapacidad;
import com.automine.nomina.NominaDtos.GenerarPeriodoResponse;
import com.automine.nomina.NominaDtos.NominaRequest;
import com.automine.nomina.NominaDtos.NominaResponse;
import com.automine.nomina.NominaDtos.NovedadesRequest;
import com.automine.nomina.NominaDtos.PeriodoResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NominaService {

    private final Nomina.Repo nominas;
    private final PeriodoNomina.Repo periodos;
    private final Empleado.Repo empleados;
    private final ParametroService parametros;
    private final AuditService audit;
    private final com.automine.notificaciones.NotificacionService notificaciones;
    private final AppProperties props;
    private final Incapacidad.Repo incapacidades;
    private final com.automine.prestaciones.Vacacion.Repo vacaciones;

    public CalculadoraNomina.Parametros parametros() {
        return new CalculadoraNomina.Parametros(parametros.decimal("SALARIO_MINIMO"),
                parametros.decimal("AUXILIO_TRANSPORTE"), parametros.decimal("HORAS_MES"),
                parametros.decimal("RECARGO_EXTRA_DIURNA"), parametros.decimal("RECARGO_EXTRA_NOCTURNA"),
                parametros.decimal("RECARGO_EXTRA_DOMINICAL"), parametros.decimal("PORC_SALUD"),
                parametros.decimal("PORC_PENSION"), parametros.decimal("PORC_SOLIDARIDAD"));
    }

    /** Servicio GeneratePayroll. RN001: un empleado no puede tener dos nominas en el mismo periodo. */
    @Transactional
    public NominaResponse liquidar(NominaRequest req) {
        Empleado e = empleados.findById(req.empleadoId()).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            throw ApiException.badRequest("No se puede liquidar nómina a un empleado inactivo");
        }
        PeriodoNomina periodo = periodoAbierto(req.anio(), req.mes());
        if (nominas.existsByPeriodoIdAndEmpleadoId(periodo.getId(), e.getId())) {
            throw ApiException.conflict("El empleado " + e.getNombreCompleto() + " ya tiene nómina en "
                    + Fmt.mes(req.mes()) + " " + req.anio() + " (RN001)");
        }
        Nomina n = new Nomina();
        n.setPeriodo(periodo);
        n.setEmpleado(e);
        n.setSalarioBase(req.salarioBase() != null ? req.salarioBase() : e.getSalario());
        n.setEstado(Nomina.PENDIENTE);
        aplicarNovedades(n, req.diasTrabajados() == null ? 30 - incapacidad(n).dias() - vacaciones(n).dias() : req.diasTrabajados(),
                req.horasExtraDiurnas(),
                req.horasExtraNocturnas(), req.horasExtraDominicales(), req.bonificaciones(), req.otrosDescuentos(),
                req.detalleDescuentos());
        nominas.save(n);
        audit.registrar("LIQUIDAR", "Nómina", n.getId(),
                e.getNombreCompleto() + " " + req.mes() + "/" + req.anio() + " neto " + Fmt.money(n.getNetoPagar()));
        return NominaResponse.of(n);
    }

    /** Genera la nomina de todos los empleados activos que aun no tienen liquidacion en el periodo. */
    @Transactional
    public GenerarPeriodoResponse generarPeriodo(int anio, int mes) {
        PeriodoNomina periodo = periodoAbierto(anio, mes);
        int creadas = 0;
        int omitidas = 0;
        for (Empleado e : empleados.findByEstadoOrderByApellidosAsc(Empleado.ACTIVO)) {
            if (nominas.existsByPeriodoIdAndEmpleadoId(periodo.getId(), e.getId())) {
                omitidas++;
                continue;
            }
            Nomina n = new Nomina();
            n.setPeriodo(periodo);
            n.setEmpleado(e);
            n.setSalarioBase(e.getSalario());
            n.setEstado(Nomina.PENDIENTE);
            aplicarNovedades(n, 30 - incapacidad(n).dias() - vacaciones(n).dias(), null, null, null, null, null, null);
            nominas.save(n);
            creadas++;
        }
        audit.registrar("GENERAR_PERIODO", "Nómina", periodo.getId(), mes + "/" + anio + ": " + creadas + " nuevas");
        PeriodoResponse r = resumen(periodo);
        if (creadas > 0) {
            notificaciones.nominaPorAprobar(anio, mes, (int) r.liquidaciones(), r.totalNeto());
        }
        return new GenerarPeriodoResponse(r, creadas, omitidas);
    }

    /** Ajuste de novedades (horas extra, descuentos, dias) mientras el periodo este abierto. */
    @Transactional
    public NominaResponse actualizar(Long id, NovedadesRequest req) {
        Nomina n = buscar(id);
        exigirAbierto(n.getPeriodo());
        aplicarNovedades(n, req.diasTrabajados(), req.horasExtraDiurnas(), req.horasExtraNocturnas(),
                req.horasExtraDominicales(), req.bonificaciones(), req.otrosDescuentos(), req.detalleDescuentos());
        audit.registrar("AJUSTAR", "Nómina", id, "neto " + Fmt.money(n.getNetoPagar()));
        return NominaResponse.of(n);
    }

    @Transactional
    public void eliminar(Long id) {
        Nomina n = buscar(id);
        exigirAbierto(n.getPeriodo());
        nominas.delete(n);
        audit.registrar("ELIMINAR", "Nómina", id, n.getEmpleado().getNombreCompleto());
    }

    /** RN005: solo Contabilidad aprueba. Bloquea el periodo y habilita las consignaciones. */
    @Transactional
    public PeriodoResponse aprobar(Long periodoId) {
        PeriodoNomina p = periodos.findById(periodoId).orElseThrow(() -> ApiException.notFound("Periodo"));
        exigirAbierto(p);
        List<Nomina> lista = nominas.findByPeriodoIdOrderByEmpleadoApellidosAsc(periodoId);
        if (lista.isEmpty()) {
            throw ApiException.badRequest("El periodo no tiene liquidaciones");
        }
        lista.forEach(n -> n.setEstado(Nomina.APROBADA));
        p.setEstado(PeriodoNomina.APROBADO);
        p.setAprobadoPor(AuditService.usuarioActual());
        p.setFechaAprobacion(LocalDateTime.now());
        audit.registrar("APROBAR", "PeriodoNomina", periodoId, p.getMes() + "/" + p.getAnio());
        notificaciones.nominaAprobada(p.getAnio(), p.getMes(), lista.stream().map(Nomina::getEmpleado).toList());
        return resumen(p);
    }

    public List<PeriodoResponse> periodos() {
        return periodos.findAllByOrderByAnioDescMesDesc().stream().map(this::resumen).toList();
    }

    public PeriodoResponse periodo(Long id) {
        return resumen(periodos.findById(id).orElseThrow(() -> ApiException.notFound("Periodo")));
    }

    public List<NominaResponse> detallePeriodo(Long periodoId) {
        return nominas.findByPeriodoIdOrderByEmpleadoApellidosAsc(periodoId).stream().map(NominaResponse::of).toList();
    }

    /** RF016: historial mensual de nomina del empleado. */
    public List<NominaResponse> historial(Long empleadoId) {
        return nominas.historialEmpleado(empleadoId).stream().map(NominaResponse::of).toList();
    }

    public Nomina buscar(Long id) {
        return nominas.findById(id).orElseThrow(() -> ApiException.notFound("Nómina"));
    }

    public NominaResponse obtener(Long id) {
        return NominaResponse.of(buscar(id));
    }

    /** RF015: desprendible de pago en PDF. */
    @Transactional(readOnly = true)
    public byte[] desprendible(Long id) {
        Nomina n = buscar(id);
        Empleado e = n.getEmpleado();
        PeriodoNomina p = n.getPeriodo();
        PdfBuilder pdf = new PdfBuilder().header(props.company().name(), props.company().nit(),
                "Comprobante de pago de nómina - " + Fmt.mes(p.getMes()) + " " + p.getAnio());
        pdf.keyValues(List.of(
                new String[] { "Empleado", e.getNombreCompleto() },
                new String[] { "Cédula", e.getCedula() },
                new String[] { "Cargo", e.getCargo() },
                new String[] { "Salario base", Fmt.money(n.getSalarioBase()) },
                new String[] { "Días trabajados", String.valueOf(n.getDiasTrabajados())
                        + (n.getDiasIncapacidad() != null && n.getDiasIncapacidad() > 0
                                ? " + " + n.getDiasIncapacidad() + " de incapacidad" : "")
                        + (n.getDiasVacaciones() != null && n.getDiasVacaciones() > 0
                                ? " + " + n.getDiasVacaciones() + " de vacaciones" : "") },
                new String[] { "Cuenta", nvl(e.getBanco()) + " " + nvl(e.getTipoCuenta()) + " " + nvl(e.getNumeroCuenta()) },
                new String[] { "Estado", n.getEstado() }));
        pdf.subtitle("Devengados");
        List<String[]> dev = new ArrayList<>();
        dev.add(new String[] { "Salario devengado", "", Fmt.money(n.getSalarioDevengado()) });
        dev.add(new String[] { "Horas extra",
                "D " + Fmt.num(n.getHorasExtraDiurnas()) + " / N " + Fmt.num(n.getHorasExtraNocturnas()) + " / F "
                        + Fmt.num(n.getHorasExtraDominicales()),
                Fmt.money(n.getValorHorasExtra()) });
        dev.add(new String[] { "Bonificaciones", "", Fmt.money(n.getBonificaciones()) });
        if (n.getDiasIncapacidad() != null && n.getDiasIncapacidad() > 0) {
            dev.add(new String[] { "Incapacidades", n.getDiasIncapacidad() + " días",
                    Fmt.money(n.getValorIncapacidad()) });
        }
        if (n.getDiasVacaciones() != null && n.getDiasVacaciones() > 0) {
            dev.add(new String[] { "Vacaciones", n.getDiasVacaciones() + " días", Fmt.money(n.getValorVacaciones()) });
        }
        dev.add(new String[] { "Auxilio de transporte", "", Fmt.money(n.getAuxilioTransporte()) });
        dev.add(new String[] { "TOTAL DEVENGADO", "", Fmt.money(n.getTotalDevengado()) });
        pdf.table(new String[] { "Concepto", "Detalle", "Valor" }, dev);
        pdf.subtitle("Deducciones");
        List<String[]> ded = new ArrayList<>();
        ded.add(new String[] { "Salud (4%)", "", Fmt.money(n.getSalud()) });
        ded.add(new String[] { "Pension (4%)", "", Fmt.money(n.getPension()) });
        ded.add(new String[] { "Fondo de solidaridad", "", Fmt.money(n.getFondoSolidaridad()) });
        ded.add(new String[] { "Otros descuentos", nvl(n.getDetalleDescuentos()), Fmt.money(n.getOtrosDescuentos()) });
        ded.add(new String[] { "TOTAL DEDUCCIONES", "", Fmt.money(n.getTotalDescuentos()) });
        pdf.table(new String[] { "Concepto", "Detalle", "Valor" }, ded);
        pdf.space(6);
        pdf.keyValues(List.<String[]>of(new String[] { "NETO A PAGAR", Fmt.money(n.getNetoPagar()) }));
        return pdf.build();
    }

    void aplicarNovedades(Nomina n, int dias, BigDecimal hed, BigDecimal hen, BigDecimal hedd, BigDecimal bonif,
            BigDecimal otros, String detalle) {
        CalculadoraIncapacidad.Resultado incap = incapacidad(n);
        CalculadoraIncapacidad.Resultado vac = vacaciones(n);
        if (dias + incap.dias() + vac.dias() > 30) {
            throw ApiException.badRequest("Los días trabajados (" + dias + ") más los de incapacidad (" + incap.dias()
                    + ") y vacaciones (" + vac.dias() + ") aprobados superan los 30 días del mes");
        }
        var in = new CalculadoraNomina.Entrada(n.getSalarioBase(), dias, nz(hed), nz(hen), nz(hedd), nz(bonif),
                nz(otros), incap.valor(), vac.valor());
        CalculadoraNomina.Resultado r;
        try {
            r = CalculadoraNomina.calcular(in, parametros());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        n.setDiasTrabajados(dias);
        n.setDiasIncapacidad(incap.dias());
        n.setValorIncapacidad(incap.valor());
        n.setDiasVacaciones(vac.dias());
        n.setValorVacaciones(vac.valor());
        n.setHorasExtraDiurnas(in.horasExtraDiurnas());
        n.setHorasExtraNocturnas(in.horasExtraNocturnas());
        n.setHorasExtraDominicales(in.horasExtraDominicales());
        n.setBonificaciones(in.bonificaciones());
        n.setOtrosDescuentos(in.otrosDescuentos());
        n.setDetalleDescuentos(detalle == null || detalle.isBlank() ? null : detalle.trim());
        n.setSalarioDevengado(r.salarioDevengado());
        n.setValorHorasExtra(r.valorHorasExtra());
        n.setAuxilioTransporte(r.auxilioTransporte());
        n.setTotalDevengado(r.totalDevengado());
        n.setSalud(r.salud());
        n.setPension(r.pension());
        n.setFondoSolidaridad(r.fondoSolidaridad());
        n.setTotalDescuentos(r.totalDescuentos());
        n.setNetoPagar(r.netoPagar());
    }

    /** Dias y valor de las incapacidades aprobadas del empleado en el mes de la nomina. */
    private CalculadoraIncapacidad.Resultado incapacidad(Nomina n) {
        PeriodoNomina p = n.getPeriodo();
        YearMonth mes = YearMonth.of(p.getAnio(), p.getMes());
        List<Incapacidad> lista = incapacidades.aprobadasEnRango(n.getEmpleado().getId(), mes.atDay(1),
                mes.atEndOfMonth());
        if (lista.isEmpty()) {
            return CalculadoraIncapacidad.Resultado.CERO;
        }
        List<CalculadoraIncapacidad.Tramo> tramos = lista.stream()
                .map(i -> new CalculadoraIncapacidad.Tramo(i.getTipo(), i.getFechaInicio(), i.getFechaFin(),
                        diasPrevios(i, 0)))
                .toList();
        return CalculadoraIncapacidad.delMes(tramos, mes, n.getSalarioBase(), parametros.decimal("SALARIO_MINIMO"));
    }

    /** Dias y valor de las vacaciones aprobadas del empleado en el mes (salario ordinario). */
    private CalculadoraIncapacidad.Resultado vacaciones(Nomina n) {
        PeriodoNomina p = n.getPeriodo();
        YearMonth mes = YearMonth.of(p.getAnio(), p.getMes());
        var lista = vacaciones.aprobadasEnRango(n.getEmpleado().getId(), mes.atDay(1), mes.atEndOfMonth());
        if (lista.isEmpty()) {
            return CalculadoraIncapacidad.Resultado.CERO;
        }
        var tramos = lista.stream()
                .map(v -> new CalculadoraIncapacidad.Tramo("VACACIONES", v.getFechaInicio(), v.getFechaFin(), 0))
                .toList();
        return CalculadoraIncapacidad.delMes(tramos, mes, n.getSalarioBase(), BigDecimal.ZERO);
    }

    /** En una prorroga el conteo de dias (1-90 al 66,67 %, 91-180 al 50 %) continua desde la anterior. */
    private int diasPrevios(Incapacidad i, int profundidad) {
        if (!i.isProrroga() || profundidad > 12) {
            return 0;
        }
        return incapacidades
                .findFirstByEmpleadoIdAndEstadoAndFechaFinBeforeOrderByFechaFinDesc(i.getEmpleado().getId(),
                        Incapacidad.APROBADA, i.getFechaInicio())
                .filter(a -> a.getTipo().equals(i.getTipo())
                        && !a.getFechaFin().isBefore(i.getFechaInicio().minusDays(30)))
                .map(a -> a.getDias() + diasPrevios(a, profundidad + 1))
                .orElse(0);
    }

    /**
     * Al aprobar o anular una incapacidad se recalculan las nominas abiertas de los meses que cubre.
     * Devuelve un aviso si alguno de esos meses ya estaba aprobado (se ajusta en el siguiente periodo).
     */
    @Transactional
    public String recalcularNovedades(Long empleadoId, LocalDate desde, LocalDate hasta) {
        List<String> cerrados = new ArrayList<>();
        for (YearMonth m = YearMonth.from(desde); !m.isAfter(YearMonth.from(hasta)); m = m.plusMonths(1)) {
            var periodo = periodos.findByAnioAndMes(m.getYear(), m.getMonthValue());
            if (periodo.isEmpty()) {
                continue;
            }
            var nomina = nominas.findByPeriodoIdAndEmpleadoId(periodo.get().getId(), empleadoId);
            if (nomina.isEmpty()) {
                continue;
            }
            Nomina n = nomina.get();
            if (!PeriodoNomina.ABIERTO.equals(periodo.get().getEstado())) {
                cerrados.add(Fmt.mes(m.getMonthValue()) + " " + m.getYear());
                continue;
            }
            int anterior = (n.getDiasIncapacidad() == null ? 0 : n.getDiasIncapacidad())
                    + (n.getDiasVacaciones() == null ? 0 : n.getDiasVacaciones());
            int nuevos = incapacidad(n).dias() + vacaciones(n).dias();
            int dias = Math.max(0, Math.min(30 - nuevos, n.getDiasTrabajados() + anterior - nuevos));
            aplicarNovedades(n, dias, n.getHorasExtraDiurnas(), n.getHorasExtraNocturnas(),
                    n.getHorasExtraDominicales(), n.getBonificaciones(), n.getOtrosDescuentos(),
                    n.getDetalleDescuentos());
            audit.registrar("NOVEDAD", "Nómina", n.getId(),
                    nuevos + " días de incapacidad/vacaciones, neto " + Fmt.money(n.getNetoPagar()));
        }
        return cerrados.isEmpty() ? null
                : "La nómina de " + String.join(", ", cerrados)
                        + " ya estaba aprobada: ajuste la diferencia en el siguiente periodo.";
    }

    private PeriodoNomina periodoAbierto(int anio, int mes) {
        PeriodoNomina p = periodos.findByAnioAndMes(anio, mes).orElseGet(() -> {
            PeriodoNomina nuevo = new PeriodoNomina();
            nuevo.setAnio(anio);
            nuevo.setMes(mes);
            nuevo.setEstado(PeriodoNomina.ABIERTO);
            return periodos.save(nuevo);
        });
        exigirAbierto(p);
        return p;
    }

    private void exigirAbierto(PeriodoNomina p) {
        if (!PeriodoNomina.ABIERTO.equals(p.getEstado())) {
            throw ApiException.badRequest("El periodo " + Fmt.mes(p.getMes()) + " " + p.getAnio()
                    + " ya fue aprobado y no admite cambios");
        }
    }

    PeriodoResponse resumen(PeriodoNomina p) {
        List<Nomina> lista = nominas.findByPeriodoIdOrderByEmpleadoApellidosAsc(p.getId());
        BigDecimal dev = BigDecimal.ZERO;
        BigDecimal desc = BigDecimal.ZERO;
        BigDecimal neto = BigDecimal.ZERO;
        for (Nomina n : lista) {
            dev = dev.add(n.getTotalDevengado());
            desc = desc.add(n.getTotalDescuentos());
            neto = neto.add(n.getNetoPagar());
        }
        return new PeriodoResponse(p.getId(), p.getAnio(), p.getMes(), p.getEstado(), lista.size(), dev, desc, neto,
                p.getAprobadoPor(), p.getFechaAprobacion());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
