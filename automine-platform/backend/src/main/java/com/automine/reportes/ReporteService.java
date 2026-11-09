package com.automine.reportes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.ExcelBuilder;
import com.automine.common.Fmt;
import com.automine.common.PdfBuilder;
import com.automine.config.AppProperties;
import com.automine.contabilidad.ContabilidadDtos.BalanceGeneral;
import com.automine.contabilidad.ContabilidadDtos.EstadoResultados;
import com.automine.contabilidad.ContabilidadDtos.Linea;
import com.automine.contabilidad.ContabilidadService;
import com.automine.contabilidad.MovimientoContable;
import com.automine.empleados.EmpleadoService;
import com.automine.inventario.InventarioService;
import com.automine.nomina.NominaService;
import com.automine.produccion.ProduccionDtos.Indicadores;
import com.automine.produccion.ProduccionService;
import com.automine.sgsst.SgsstService;

import lombok.RequiredArgsConstructor;

/** RF046, RF047, RF049: reportes exportables en PDF y Excel. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    private final EmpleadoService empleados;
    private final NominaService nomina;
    private final InventarioService inventario;
    private final ProduccionService produccion;
    private final ContabilidadService contabilidad;
    private final SgsstService sgsst;
    private final DashboardService dashboard;
    private final AppProperties props;

    /** Una seccion es una tabla: encabezados y filas con valores tipados. */
    public record Seccion(String titulo, String[] encabezados, List<Object[]> filas) {
    }

    public record Reporte(String titulo, String subtitulo, String archivo, List<Seccion> secciones, boolean horizontal) {
    }

    public enum Tipo {
        EMPLEADOS, NOMINA, INVENTARIO, PRODUCCION, CONTABILIDAD, SGSST, MENSUAL
    }

    public Reporte construir(Tipo tipo, LocalDate desde, LocalDate hasta, Long periodoId) {
        ContabilidadService.validarRango(desde, hasta);
        String rango = Fmt.date(desde) + " a " + Fmt.date(hasta);
        return switch (tipo) {
            case EMPLEADOS -> empleados(rango);
            case NOMINA -> nomina(periodoId);
            case INVENTARIO -> inventario(desde, hasta, rango);
            case PRODUCCION -> produccion(desde, hasta, rango);
            case CONTABILIDAD -> contabilidad(desde, hasta, rango);
            case SGSST -> sgsst(desde, hasta, rango);
            case MENSUAL -> mensual(YearMonth.from(desde));
        };
    }

    public byte[] pdf(Reporte r) {
        PdfBuilder pdf = new PdfBuilder(r.horizontal()).header(props.company().name(), props.company().nit(),
                r.titulo());
        pdf.small(r.subtitulo());
        for (Seccion s : r.secciones()) {
            pdf.subtitle(s.titulo());
            List<String[]> filas = new ArrayList<>();
            for (Object[] f : s.filas()) {
                String[] out = new String[f.length];
                for (int i = 0; i < f.length; i++) {
                    out[i] = texto(f[i], s.encabezados()[i]);
                }
                filas.add(out);
            }
            pdf.table(s.encabezados(), filas);
        }
        return pdf.build();
    }

    public byte[] excel(Reporte r) {
        ExcelBuilder x = new ExcelBuilder();
        int n = 1;
        for (Seccion s : r.secciones()) {
            String nombre = (n++ + " " + s.titulo()).replaceAll("[\\\\/?*\\[\\]:]", " ");
            x.sheet(nombre.length() > 31 ? nombre.substring(0, 31) : nombre, s.encabezados(), s.filas());
        }
        return x.build();
    }

    // ------------------------------- Reportes -------------------------------

    private Reporte empleados(String rango) {
        List<Object[]> filas = empleados.listar(null, null).stream().map(e -> new Object[] { e.cedula(),
                e.nombreCompleto(), e.cargo(), e.area(), e.tipoContrato(), e.fechaIngreso(), e.salario(), e.estado() })
                .toList();
        return new Reporte("Reporte de personal", "Generado " + Fmt.dateTime(LocalDateTime.now()), "empleados",
                List.of(new Seccion("Empleados", new String[] { "Cédula", "Nombre", "Cargo", "Área", "Contrato",
                        "Ingreso", "Salario $", "Estado" }, filas)),
                true);
    }

    private Reporte nomina(Long periodoId) {
        if (periodoId == null) {
            throw ApiException.badRequest("Seleccione el periodo de nómina");
        }
        var p = nomina.periodo(periodoId);
        List<Object[]> filas = nomina.detallePeriodo(periodoId).stream().map(n -> new Object[] { n.cedula(),
                n.empleado(), n.diasTrabajados(), n.salarioDevengado(), n.valorHorasExtra(), n.bonificaciones(),
                n.auxilioTransporte(), n.totalDevengado(), n.salud().add(n.pension()).add(n.fondoSolidaridad()),
                n.otrosDescuentos(), n.netoPagar(), n.estado() }).toList();
        List<Object[]> totales = List.<Object[]>of(new Object[] { p.liquidaciones(), p.totalDevengado(),
                p.totalDescuentos(), p.totalNeto(), p.estado() });
        return new Reporte("Nómina " + Fmt.mes(p.mes()) + " " + p.anio(), "Estado del periodo: " + p.estado(),
                "nomina-" + p.anio() + "-" + p.mes(),
                List.of(new Seccion("Liquidaciones", new String[] { "Cédula", "Empleado", "Días", "Salario $",
                        "Extras $", "Bonif. $", "Aux. transp. $", "Devengado $", "Seg. social $", "Otros desc. $",
                        "Neto $", "Estado" }, filas),
                        new Seccion("Totales", new String[] { "Liquidaciones", "Devengado $", "Descuentos $",
                                "Neto $", "Estado" }, totales)),
                true);
    }

    private Reporte inventario(LocalDate desde, LocalDate hasta, String rango) {
        List<Object[]> stock = inventario.listar(null, null, true).stream().map(p -> new Object[] { p.codigo(),
                p.nombre(), p.tipo(), p.unidad(), p.cantidad(), p.stockMinimo(), p.costoUnitario(), p.valorTotal(),
                p.bajoStock() ? "BAJO" : "OK" }).toList();
        List<Object[]> movs = inventario.recientes(200).stream()
                .filter(m -> !m.fecha().toLocalDate().isBefore(desde) && !m.fecha().toLocalDate().isAfter(hasta))
                .map(m -> new Object[] { m.fecha(), m.productoCodigo(), m.producto(), m.tipo(), m.cantidad(),
                        m.saldo(), m.motivo(), m.responsable() })
                .toList();
        List<Object[]> maq = inventario.maquinas().stream().map(m -> new Object[] { m.getCodigo(), m.getNombre(),
                m.getTipo(), m.getEstado(), m.getHorometro(), m.getProximoMantenimiento() }).toList();
        return new Reporte("Reporte de inventario", "Movimientos del " + rango, "inventario",
                List.of(new Seccion("Existencias", new String[] { "Código", "Producto", "Tipo", "Unidad", "Cantidad",
                        "Mínimo", "Costo unit. $", "Valor $", "Alerta" }, stock),
                        new Seccion("Movimientos", new String[] { "Fecha", "Código", "Producto", "Tipo", "Cantidad",
                                "Saldo", "Motivo", "Responsable" }, movs),
                        new Seccion("Maquinaria", new String[] { "Código", "Máquina", "Tipo", "Estado", "Horómetro",
                                "Prox. mantenimiento" }, maq)),
                true);
    }

    private Reporte produccion(LocalDate desde, LocalDate hasta, String rango) {
        Indicadores ind = produccion.indicadores(desde, hasta);
        List<Object[]> registros = produccion.listar(desde, hasta, null).stream().map(p -> new Object[] { p.fecha(),
                p.turno(), p.frente(), p.mineral(), p.toneladas(), p.horasOperacion(), p.personal(), p.observaciones() })
                .toList();
        List<Object[]> frentes = ind.porFrente().stream()
                .map(c -> new Object[] { c.frente(), c.toneladas(), c.meta(), c.porcentaje() }).toList();
        List<Object[]> resumen = List.<Object[]>of(new Object[] { ind.totalToneladas(), ind.promedioDiario(),
                ind.toneladasPorHora(), ind.toneladasPorTrabajador(), ind.diasConRegistro() });
        return new Reporte("Reporte de producción minera", rango, "produccion",
                List.of(new Seccion("Indicadores", new String[] { "Total t", "Promedio diario t", "t por hora",
                        "t por trabajador", "Días con registro" }, resumen),
                        new Seccion("Cumplimiento por frente", new String[] { "Frente", "Toneladas", "Meta",
                                "Cumplimiento %" }, frentes),
                        new Seccion("Registros por turno", new String[] { "Fecha", "Turno", "Frente", "Mineral",
                                "Toneladas", "Horas", "Personal", "Observaciones" }, registros)),
                true);
    }

    private Reporte contabilidad(LocalDate desde, LocalDate hasta, String rango) {
        EstadoResultados er = contabilidad.estadoResultados(desde, hasta);
        BalanceGeneral bg = contabilidad.balance(hasta);
        List<Object[]> resultados = new ArrayList<>();
        er.ingresos().forEach(l -> resultados.add(new Object[] { "Ingreso", l.concepto(), l.valor() }));
        resultados.add(new Object[] { "", "TOTAL INGRESOS", er.totalIngresos() });
        er.gastos().forEach(l -> resultados.add(new Object[] { "Gasto", l.concepto(), l.valor() }));
        resultados.add(new Object[] { "", "TOTAL GASTOS", er.totalGastos() });
        resultados.add(new Object[] { "", "UTILIDAD DEL PERIODO", er.utilidad() });
        List<Object[]> balance = new ArrayList<>();
        bg.activos().forEach(l -> balance.add(linea("Activo", l)));
        balance.add(new Object[] { "", "TOTAL ACTIVOS", bg.totalActivos() });
        bg.pasivos().forEach(l -> balance.add(linea("Pasivo", l)));
        balance.add(new Object[] { "", "TOTAL PASIVOS", bg.totalPasivos() });
        bg.patrimonio().forEach(l -> balance.add(linea("Patrimonio", l)));
        balance.add(new Object[] { "", "TOTAL PATRIMONIO", bg.totalPatrimonio() });
        List<Object[]> movs = new ArrayList<>();
        contabilidad.listar(MovimientoContable.INGRESO, desde, hasta).forEach(m -> movs.add(mov(m)));
        contabilidad.listar(MovimientoContable.GASTO, desde, hasta).forEach(m -> movs.add(mov(m)));
        return new Reporte("Reporte contable", rango, "contabilidad",
                List.of(new Seccion("Estado de resultados", new String[] { "Tipo", "Concepto", "Valor $" }, resultados),
                        new Seccion("Balance general al " + Fmt.date(hasta), new String[] { "Grupo", "Cuenta",
                                "Valor $" }, balance),
                        new Seccion("Movimientos", new String[] { "Fecha", "Tipo", "Categoría", "Concepto",
                                "Referencia", "Origen", "Valor $" }, movs)),
                false);
    }

    private Reporte sgsst(LocalDate desde, LocalDate hasta, String rango) {
        List<Object[]> eventos = sgsst.eventos(null, desde, hasta).stream().map(e -> new Object[] { e.fecha(),
                e.tipo(), e.empleado(), e.lugar(), e.gravedad(), e.diasIncapacidad(), e.estado(), e.descripcion() })
                .toList();
        List<Object[]> epp = sgsst.entregas(null, desde, hasta).stream().map(e -> new Object[] { e.fecha(),
                e.empleado(), e.elemento(), e.cantidad(), e.entregadoPor() }).toList();
        List<Object[]> cap = sgsst.capacitaciones(desde, hasta).stream().map(c -> new Object[] { c.fecha(), c.tema(),
                c.instructor(), c.duracionHoras(), c.convocados(), c.asistentes(), c.estado() }).toList();
        List<Object[]> riesgos = sgsst.riesgos().stream().map(r -> new Object[] { r.getProceso(), r.getActividad(),
                r.getPeligro(), r.getClasificacion(), r.getProbabilidad(), r.getSeveridad(), r.getNivel(),
                r.getInterpretacion(), r.getControles() }).toList();
        return new Reporte("Reporte SG-SST", rango, "sgsst",
                List.of(new Seccion("Accidentes e incidentes", new String[] { "Fecha", "Tipo", "Trabajador", "Lugar",
                        "Gravedad", "Días incap.", "Estado", "Descripción" }, eventos),
                        new Seccion("Entregas de EPP", new String[] { "Fecha", "Trabajador", "Elemento", "Cantidad",
                                "Entregado por" }, epp),
                        new Seccion("Capacitaciones", new String[] { "Fecha", "Tema", "Instructor", "Horas",
                                "Convocados", "Asistentes", "Estado" }, cap),
                        new Seccion("Matriz de riesgos", new String[] { "Proceso", "Actividad", "Peligro",
                                "Clasificación", "Prob.", "Sev.", "Nivel", "Interpretación", "Controles" }, riesgos)),
                true);
    }

    /** RF049: reporte gerencial mensual consolidado. */
    private Reporte mensual(YearMonth mes) {
        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : mes.atEndOfMonth();
        List<Object[]> kpis = dashboard.kpis(mes, hasta).stream()
                .map(k -> new Object[] { k.nombre(), k.valor(), k.unidad(), k.anterior(),
                        k.variacion() == null ? null : k.variacion() + " %" })
                .toList();
        Indicadores ind = produccion.indicadores(desde, hasta);
        List<Object[]> frentes = ind.porFrente().stream()
                .map(c -> new Object[] { c.frente(), c.toneladas(), c.meta(), c.porcentaje() }).toList();
        EstadoResultados er = contabilidad.estadoResultados(desde, hasta);
        List<Object[]> fin = new ArrayList<>();
        er.ingresos().forEach(l -> fin.add(new Object[] { "Ingreso", l.concepto(), l.valor() }));
        er.gastos().forEach(l -> fin.add(new Object[] { "Gasto", l.concepto(), l.valor() }));
        fin.add(new Object[] { "", "UTILIDAD", er.utilidad() });
        var sst = sgsst.resumen();
        List<Object[]> sstRows = List.<Object[]>of(new Object[] { sst.accidentesMes(), sst.incidentesMes(),
                sst.diasIncapacidadMes(), sst.eventosAbiertos(), sst.riesgosAltos() });
        return new Reporte("Informe gerencial " + Fmt.mes(mes.getMonthValue()) + " " + mes.getYear(),
                "Periodo " + Fmt.date(desde) + " a " + Fmt.date(hasta), "informe-mensual-" + mes,
                List.of(new Seccion("Indicadores clave (KPI)", new String[] { "Indicador", "Valor", "Unidad",
                        "Mes anterior", "Variación" }, kpis),
                        new Seccion("Producción por frente", new String[] { "Frente", "Toneladas", "Meta",
                                "Cumplimiento %" }, frentes),
                        new Seccion("Resultados financieros", new String[] { "Tipo", "Concepto", "Valor $" }, fin),
                        new Seccion("Seguridad y salud en el trabajo (mes en curso)", new String[] { "Accidentes",
                                "Incidentes", "Días incapacidad", "Casos abiertos", "Riesgos altos" }, sstRows)),
                false);
    }

    private static Object[] linea(String grupo, Linea l) {
        return new Object[] { grupo, l.concepto(), l.valor() };
    }

    private static Object[] mov(MovimientoContable m) {
        return new Object[] { m.getFecha(), m.getTipo(), m.getCategoria(), m.getConcepto(), m.getReferencia(),
                m.getOrigen(), m.getValor() };
    }

    private static String texto(Object v, String encabezado) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal bd) {
            return encabezado.endsWith("$") ? Fmt.money(bd) : Fmt.num(bd);
        }
        if (v instanceof LocalDate d) {
            return Fmt.date(d);
        }
        if (v instanceof LocalDateTime dt) {
            return Fmt.dateTime(dt);
        }
        if (v instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        return v.toString();
    }
}
