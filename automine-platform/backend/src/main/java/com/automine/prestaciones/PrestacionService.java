package com.automine.prestaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.ParametroService;
import com.automine.common.PdfBuilder;
import com.automine.common.Roles;
import com.automine.config.AppProperties;
import com.automine.empleados.Empleado;
import com.automine.empleados.EmpleadoDtos.InactivarRequest;
import com.automine.empleados.EmpleadoService;
import com.automine.nomina.Nomina;
import com.automine.nomina.PeriodoNomina;
import com.automine.prestaciones.PrestacionesDtos.LiquidacionRequest;
import com.automine.prestaciones.PrestacionesDtos.LiquidacionResponse;
import com.automine.prestaciones.PrestacionesDtos.LiquidarPrestacionRequest;
import com.automine.prestaciones.PrestacionesDtos.PagoPrestacionResponse;
import com.automine.prestaciones.PrestacionesDtos.PrestacionCalculo;
import com.automine.prestaciones.PrestacionesDtos.PrestacionPeriodo;
import com.automine.prestaciones.PrestacionesDtos.SaldoVacaciones;
import com.automine.security.UsuarioActual;

import lombok.RequiredArgsConstructor;

/** Prima de servicios, cesantias con sus intereses y liquidacion final del contrato. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrestacionService {

    private final PagoPrestacion.Repo pagos;
    private final LiquidacionRetiro.Repo liquidaciones;
    private final Vacacion.Repo vacaciones;
    private final Empleado.Repo empleados;
    private final Nomina.Repo nominas;
    private final PeriodoNomina.Repo periodos;
    private final EmpleadoService empleadoService;
    private final VacacionService vacacionService;
    private final ParametroService parametros;
    private final AuditService audit;
    private final AppProperties props;

    // ------------------------------ Prima y cesantias ------------------------------

    public PrestacionPeriodo calcular(String tipo, int anio, Integer periodoParam) {
        int periodo = normalizarPeriodo(tipo, periodoParam);
        LocalDate desde = PagoPrestacion.PRIMA.equals(tipo) ? LocalDate.of(anio, periodo == 1 ? 1 : 7, 1)
                : LocalDate.of(anio, 1, 1);
        LocalDate hasta = PagoPrestacion.PRIMA.equals(tipo) ? LocalDate.of(anio, periodo == 1 ? 6 : 12, periodo == 1 ? 30 : 31)
                : LocalDate.of(anio, 12, 31);
        BigDecimal minimo = parametros.decimal("SALARIO_MINIMO");
        BigDecimal auxilio = parametros.decimal("AUXILIO_TRANSPORTE");
        List<PrestacionCalculo> detalle = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalIntereses = BigDecimal.ZERO;
        int pendientes = 0;
        for (Empleado e : empleados.findByEstadoOrderByApellidosAsc(Empleado.ACTIVO)) {
            if (!CalculadoraPrestaciones.aplica(e.getTipoContrato()) || e.getFechaIngreso().isAfter(hasta)) {
                continue;
            }
            LocalDate d = e.getFechaIngreso().isAfter(desde) ? e.getFechaIngreso() : desde;
            int dias = CalculadoraPrestaciones.dias360(d, hasta);
            BigDecimal base = CalculadoraPrestaciones.base(e.getSalario(), auxilio, minimo);
            BigDecimal valor = CalculadoraPrestaciones.prima(base, dias);
            BigDecimal intereses = PagoPrestacion.CESANTIAS.equals(tipo) ? CalculadoraPrestaciones.intereses(valor, dias)
                    : BigDecimal.ZERO.setScale(2);
            boolean pagado = pagos.existsByEmpleadoIdAndTipoAndAnioAndPeriodo(e.getId(), tipo, anio, periodo);
            if (!pagado) {
                pendientes++;
            }
            total = total.add(valor);
            totalIntereses = totalIntereses.add(intereses);
            detalle.add(new PrestacionCalculo(e.getId(), e.getCedula(), e.getNombreCompleto(), e.getCargo(), d, hasta,
                    base, dias, valor, intereses, pagado));
        }
        String limite = PagoPrestacion.PRIMA.equals(tipo)
                ? (periodo == 1 ? "30 de junio de " + anio : "20 de diciembre de " + anio)
                : "Cesantías: consignar al fondo antes del 14 de febrero de " + (anio + 1)
                        + " · Intereses: pagar al trabajador antes del 31 de enero de " + (anio + 1);
        return new PrestacionPeriodo(tipo, anio, periodo, desde, hasta, limite, total, totalIntereses, pendientes,
                detalle);
    }

    /** Registra la liquidacion para los trabajadores que aun no la tienen en el periodo. */
    @Transactional
    public PrestacionPeriodo liquidar(LiquidarPrestacionRequest req) {
        PrestacionPeriodo p = calcular(req.tipo(), req.anio(), req.periodo());
        String usuario = UsuarioActual.get().nombre();
        int creados = 0;
        for (PrestacionCalculo c : p.detalle()) {
            if (c.pagado()) {
                continue;
            }
            Empleado e = empleados.getReferenceById(c.empleadoId());
            pagos.save(pago(e, req.tipo(), p, c, c.valor(), usuario));
            if (PagoPrestacion.CESANTIAS.equals(req.tipo())) {
                pagos.save(pago(e, PagoPrestacion.INTERESES, p, c, c.intereses(), usuario));
            }
            creados++;
        }
        if (creados == 0) {
            throw ApiException.badRequest("Todos los trabajadores ya tienen liquidado este periodo");
        }
        audit.registrar("LIQUIDAR", "Prestaciones", null,
                req.tipo() + " " + req.anio() + (p.periodo() > 0 ? "-" + p.periodo() : "") + ": " + creados + " trabajadores");
        return calcular(req.tipo(), req.anio(), req.periodo());
    }

    private static PagoPrestacion pago(Empleado e, String tipo, PrestacionPeriodo p, PrestacionCalculo c,
            BigDecimal valor, String usuario) {
        PagoPrestacion x = new PagoPrestacion();
        x.setEmpleado(e);
        x.setTipo(tipo);
        x.setAnio(p.anio());
        x.setPeriodo(p.periodo());
        x.setFechaDesde(c.desde());
        x.setFechaHasta(c.hasta());
        x.setBase(c.base());
        x.setDias(c.dias());
        x.setValor(valor);
        x.setLiquidadoPor(usuario);
        return x;
    }

    public List<PagoPrestacionResponse> historial(Long empleadoId) {
        UsuarioActual.verificarEmpleado(empleadoId, Roles.RRHH, Roles.CONTADOR, Roles.GERENTE);
        return pagos.findByEmpleadoIdOrderByAnioDescPeriodoDescTipoAsc(empleadoId).stream()
                .map(PagoPrestacionResponse::of).toList();
    }

    private static int normalizarPeriodo(String tipo, Integer periodo) {
        if (PagoPrestacion.PRIMA.equals(tipo)) {
            if (periodo == null || (periodo != 1 && periodo != 2)) {
                throw ApiException.badRequest("Indique el semestre de la prima (1 o 2)");
            }
            return periodo;
        }
        if (!PagoPrestacion.CESANTIAS.equals(tipo)) {
            throw ApiException.badRequest("Tipo de prestación no válido");
        }
        return 0;
    }

    // ------------------------------ Liquidacion de retiro ------------------------------

    public LiquidacionResponse simularRetiro(Long empleadoId, LiquidacionRequest req) {
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        LiquidacionRetiro l = calcularRetiro(e, req);
        l.setLiquidadoPor(UsuarioActual.get().nombre());
        return LiquidacionResponse.of(l);
    }

    @Transactional
    public LiquidacionResponse confirmarRetiro(Long empleadoId, LiquidacionRequest req) {
        Empleado e = empleados.findById(empleadoId).orElseThrow(() -> ApiException.notFound("Empleado"));
        if (!e.isActivo()) {
            throw ApiException.badRequest("El empleado ya está retirado");
        }
        if (req.fechaRetiro().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Confirme la liquidación el día del retiro o después (puede simularla antes)");
        }
        LiquidacionRetiro l = calcularRetiro(e, req);
        l.setLiquidadoPor(UsuarioActual.get().nombre());
        liquidaciones.save(l);
        // Las vacaciones pendientes por disfrutar se compensan en dinero
        vacaciones.buscar(e.getId(), Vacacion.PENDIENTE).forEach(v -> {
            v.setEstado(Vacacion.ANULADA);
            v.setMotivoRechazo("Retiro del trabajador");
        });
        empleadoService.inactivar(e.getId(), new InactivarRequest(req.fechaRetiro(),
                "Liquidación: " + req.motivo().toLowerCase().replace('_', ' ')
                        + (req.observaciones() == null || req.observaciones().isBlank() ? "" : " - " + req.observaciones())));
        audit.registrar("LIQUIDAR_RETIRO", "Empleado", e.getId(), "Total " + Fmt.money(l.getTotal()));
        return LiquidacionResponse.of(l);
    }

    public List<LiquidacionResponse> liquidaciones() {
        return liquidaciones.findAllByOrderByFechaRetiroDesc().stream().map(LiquidacionResponse::of).toList();
    }

    LiquidacionRetiro calcularRetiro(Empleado e, LiquidacionRequest req) {
        LocalDate retiro = req.fechaRetiro();
        if (retiro.isBefore(e.getFechaIngreso())) {
            throw ApiException.badRequest("La fecha de retiro no puede ser anterior al ingreso ("
                    + Fmt.date(e.getFechaIngreso()) + ")");
        }
        if (!CalculadoraPrestaciones.aplica(e.getTipoContrato())) {
            throw ApiException.badRequest("El contrato " + e.getTipoContrato().toLowerCase().replace('_', ' ')
                    + " no genera prestaciones sociales; use la opción Inactivar");
        }
        if ("SIN_JUSTA_CAUSA".equals(req.motivo()) && !"INDEFINIDO".equals(e.getTipoContrato())
                && req.fechaFinContrato() == null) {
            throw ApiException.badRequest("Indique la fecha en que terminaba el contrato para calcular la indemnización");
        }
        BigDecimal minimo = parametros.decimal("SALARIO_MINIMO");
        BigDecimal auxilio = parametros.decimal("AUXILIO_TRANSPORTE");
        BigDecimal salario = e.getSalario();
        BigDecimal base = CalculadoraPrestaciones.base(salario, auxilio, minimo);
        int anio = retiro.getYear();

        // Salario del ultimo mes si aun no se liquido en nomina
        LocalDate inicioMes = retiro.withDayOfMonth(1);
        boolean nominaDelMes = periodos.findByAnioAndMes(anio, retiro.getMonthValue())
                .map(p -> nominas.existsByPeriodoIdAndEmpleadoId(p.getId(), e.getId())).orElse(false);
        int diasSalario = nominaDelMes ? 0
                : CalculadoraPrestaciones.dias360(e.getFechaIngreso().isAfter(inicioMes) ? e.getFechaIngreso() : inicioMes, retiro);
        BigDecimal salarioPendiente = CalculadoraPrestaciones.salarioDias(salario, diasSalario);

        // Prima del semestre
        int semestre = retiro.getMonthValue() <= 6 ? 1 : 2;
        LocalDate iniSem = LocalDate.of(anio, semestre == 1 ? 1 : 7, 1);
        int diasPrima = CalculadoraPrestaciones.dias360(e.getFechaIngreso().isAfter(iniSem) ? e.getFechaIngreso() : iniSem, retiro);
        BigDecimal prima = CalculadoraPrestaciones.prima(base, diasPrima)
                .subtract(pagos.pagado(e.getId(), PagoPrestacion.PRIMA, anio, semestre)).max(BigDecimal.ZERO);

        // Cesantias e intereses del año
        LocalDate iniAnio = LocalDate.of(anio, 1, 1);
        int diasCes = CalculadoraPrestaciones.dias360(e.getFechaIngreso().isAfter(iniAnio) ? e.getFechaIngreso() : iniAnio, retiro);
        BigDecimal cesantiasCompletas = CalculadoraPrestaciones.cesantias(base, diasCes);
        BigDecimal cesantias = cesantiasCompletas.subtract(pagos.pagado(e.getId(), PagoPrestacion.CESANTIAS, anio, 0))
                .max(BigDecimal.ZERO);
        BigDecimal intereses = CalculadoraPrestaciones.intereses(cesantiasCompletas, diasCes)
                .subtract(pagos.pagado(e.getId(), PagoPrestacion.INTERESES, anio, 0)).max(BigDecimal.ZERO);

        // Vacaciones causadas y no disfrutadas
        SaldoVacaciones saldo = vacacionService.saldo(e);
        int laborados = CalculadoraPrestaciones.dias360(e.getFechaIngreso(), retiro);
        BigDecimal diasVac = CalculadoraPrestaciones.diasVacacionesCausados(laborados)
                .subtract(BigDecimal.valueOf(saldo.diasDisfrutados())).max(BigDecimal.ZERO);
        BigDecimal vac = CalculadoraPrestaciones.compensacionVacaciones(salario, diasVac);

        BigDecimal indemnizacion = "SIN_JUSTA_CAUSA".equals(req.motivo())
                ? CalculadoraPrestaciones.indemnizacion(e.getTipoContrato(), salario, minimo, e.getFechaIngreso(),
                        retiro, req.fechaFinContrato())
                : BigDecimal.ZERO.setScale(2);

        LiquidacionRetiro l = new LiquidacionRetiro();
        l.setEmpleado(e);
        l.setFechaRetiro(retiro);
        l.setMotivo(req.motivo());
        l.setObservaciones(req.observaciones() == null || req.observaciones().isBlank() ? null : req.observaciones().trim());
        l.setSalarioBase(salario);
        l.setBasePrestaciones(base);
        l.setDiasSalario(diasSalario);
        l.setSalarioPendiente(salarioPendiente);
        l.setDiasPrima(diasPrima);
        l.setPrima(prima);
        l.setDiasCesantias(diasCes);
        l.setCesantias(cesantias);
        l.setInteresesCesantias(intereses);
        l.setDiasVacaciones(diasVac);
        l.setVacaciones(vac);
        l.setIndemnizacion(indemnizacion);
        l.setTotal(salarioPendiente.add(prima).add(cesantias).add(intereses).add(vac).add(indemnizacion));
        return l;
    }

    public byte[] pdfRetiro(Long id) {
        LiquidacionRetiro l = liquidaciones.findWithEmpleadoById(id).orElseThrow(() -> ApiException.notFound("Liquidación"));
        Empleado e = l.getEmpleado();
        PdfBuilder pdf = new PdfBuilder().header(props.company().name(), props.company().nit(),
                "Liquidación final de contrato de trabajo");
        pdf.keyValues(List.of(
                new String[] { "Trabajador", e.getNombreCompleto() },
                new String[] { "Cédula", e.getCedula() },
                new String[] { "Cargo", e.getCargo() },
                new String[] { "Tipo de contrato", etiqueta(e.getTipoContrato()) },
                new String[] { "Fecha de ingreso", Fmt.date(e.getFechaIngreso()) },
                new String[] { "Fecha de retiro", Fmt.date(l.getFechaRetiro()) },
                new String[] { "Motivo", etiqueta(l.getMotivo()) },
                new String[] { "Salario básico", Fmt.money(l.getSalarioBase()) },
                new String[] { "Base de prestaciones", Fmt.money(l.getBasePrestaciones()) }));
        pdf.subtitle("Conceptos");
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[] { "Salario pendiente", l.getDiasSalario() + " días", Fmt.money(l.getSalarioPendiente()) });
        filas.add(new String[] { "Prima de servicios", l.getDiasPrima() + " días", Fmt.money(l.getPrima()) });
        filas.add(new String[] { "Cesantías", l.getDiasCesantias() + " días", Fmt.money(l.getCesantias()) });
        filas.add(new String[] { "Intereses sobre cesantías (12 %)", "", Fmt.money(l.getInteresesCesantias()) });
        filas.add(new String[] { "Vacaciones no disfrutadas", Fmt.num(l.getDiasVacaciones()) + " días hábiles",
                Fmt.money(l.getVacaciones()) });
        if (l.getIndemnizacion().signum() > 0) {
            filas.add(new String[] { "Indemnización (art. 64 CST)", "", Fmt.money(l.getIndemnizacion()) });
        }
        filas.add(new String[] { "TOTAL A PAGAR", "", Fmt.money(l.getTotal()) });
        pdf.table(new String[] { "Concepto", "Detalle", "Valor" }, filas);
        if (l.getObservaciones() != null) {
            pdf.paragraph("Observaciones: " + l.getObservaciones());
        }
        pdf.small("Valores calculados en días comerciales (año de 360 días). El salario pendiente no incluye "
                + "deducciones de seguridad social, que se aplican en la nómina del mes.");
        pdf.space(30);
        pdf.signature(e.getNombreCompleto(), "C.C. " + e.getCedula() + " - Recibí a satisfacción", "");
        pdf.signature(l.getLiquidadoPor(), "Talento Humano", props.company().name());
        return pdf.build();
    }

    private static String etiqueta(String codigo) {
        String t = codigo.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
