package com.automine.reportes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.contabilidad.ContabilidadDtos.TendenciaMes;
import com.automine.contabilidad.ContabilidadService;
import com.automine.contabilidad.MovimientoContable;
import com.automine.empleados.Empleado;
import com.automine.inventario.InventarioService;
import com.automine.inventario.Maquinaria;
import com.automine.nomina.Nomina;
import com.automine.nomina.PeriodoNomina;
import com.automine.produccion.ProduccionDtos.Indicadores;
import com.automine.produccion.ProduccionService;
import com.automine.sgsst.SgsstDtos.ResumenSst;
import com.automine.sgsst.SgsstService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final Empleado.Repo empleados;
    private final Nomina.Repo nominas;
    private final PeriodoNomina.Repo periodos;
    private final MovimientoContable.Repo movimientos;
    private final Maquinaria.Repo maquinas;
    private final ProduccionService produccion;
    private final ContabilidadService contabilidad;
    private final InventarioService inventario;
    private final SgsstService sgsst;

    public record Kpi(String clave, String nombre, BigDecimal valor, String unidad, BigDecimal anterior,
            BigDecimal variacion) {
    }

    public record Alerta(String tipo, String nivel, String mensaje) {
    }

    public record Dashboard(LocalDate fecha, List<Kpi> kpis, Indicadores produccionMes,
            List<TendenciaMes> finanzas, ResumenSst sst, List<Alerta> alertas) {
    }

    /** Servicio Dashboard (RF048). */
    public Dashboard dashboard() {
        LocalDate hoy = LocalDate.now();
        YearMonth mes = YearMonth.from(hoy);
        Indicadores prodMes = produccion.indicadores(mes.atDay(1), hoy);
        List<TendenciaMes> finanzas = contabilidad.tendencia(mes.minusMonths(5).atDay(1), hoy);
        ResumenSst sst = sgsst.resumen();
        return new Dashboard(hoy, kpis(mes, hoy), prodMes, finanzas, sst, alertas(sst));
    }

    /** RF050: indicadores clave de un mes comparados con el mes anterior. */
    public List<Kpi> kpis(YearMonth mes, LocalDate corte) {
        LocalDate desde = mes.atDay(1);
        LocalDate hasta = corte != null && corte.isBefore(mes.atEndOfMonth()) ? corte : mes.atEndOfMonth();
        YearMonth prev = mes.minusMonths(1);
        LocalDate pDesde = prev.atDay(1);
        // Mes en curso: se compara contra los mismos dias del mes anterior.
        LocalDate pHasta = hasta.equals(mes.atEndOfMonth()) ? prev.atEndOfMonth()
                : prev.atDay(Math.min(hasta.getDayOfMonth(), prev.lengthOfMonth()));

        Indicadores ind = produccion.indicadores(desde, hasta);
        Indicadores indPrev = produccion.indicadores(pDesde, pHasta);
        BigDecimal ingresos = movimientos.total(MovimientoContable.INGRESO, desde, hasta);
        BigDecimal gastos = movimientos.total(MovimientoContable.GASTO, desde, hasta);
        BigDecimal ingresosPrev = movimientos.total(MovimientoContable.INGRESO, pDesde, pHasta);
        BigDecimal gastosPrev = movimientos.total(MovimientoContable.GASTO, pDesde, pHasta);
        BigDecimal nomina = nominas.costoPeriodo(mes.getYear(), mes.getMonthValue());
        BigDecimal nominaPrev = nominas.costoPeriodo(prev.getYear(), prev.getMonthValue());

        BigDecimal cumplimiento = cumplimiento(ind);
        BigDecimal cumplimientoPrev = cumplimiento(indPrev);
        BigDecimal costoTon = ind.totalToneladas().signum() == 0 ? BigDecimal.ZERO
                : gastos.divide(ind.totalToneladas(), 0, RoundingMode.HALF_UP);
        BigDecimal costoTonPrev = indPrev.totalToneladas().signum() == 0 ? BigDecimal.ZERO
                : gastosPrev.divide(indPrev.totalToneladas(), 0, RoundingMode.HALF_UP);

        List<Kpi> out = new ArrayList<>();
        out.add(kpi("toneladas", "Producción del mes", ind.totalToneladas(), "t", indPrev.totalToneladas()));
        out.add(kpi("cumplimiento", "Cumplimiento de meta", cumplimiento, "%", cumplimientoPrev));
        out.add(kpi("productividad", "Toneladas por hora", ind.toneladasPorHora(), "t/h", indPrev.toneladasPorHora()));
        out.add(kpi("ingresos", "Ingresos", ingresos, "COP", ingresosPrev));
        out.add(kpi("gastos", "Gastos", gastos, "COP", gastosPrev));
        out.add(kpi("utilidad", "Utilidad", ingresos.subtract(gastos), "COP", ingresosPrev.subtract(gastosPrev)));
        out.add(kpi("costoTonelada", "Costo por tonelada", costoTon, "COP/t", costoTonPrev));
        out.add(kpi("nomina", "Costo de nómina", nomina, "COP", nominaPrev));
        out.add(kpi("empleados", "Empleados activos", BigDecimal.valueOf(empleados.countByEstado(Empleado.ACTIVO)),
                "personas", null));
        out.add(kpi("inventario", "Valor del inventario", inventario.valorInventario(), "COP", null));
        return out;
    }

    private List<Alerta> alertas(ResumenSst sst) {
        List<Alerta> out = new ArrayList<>();
        inventario.alertasStock().forEach(p -> out.add(new Alerta("INVENTARIO",
                p.cantidad().signum() == 0 ? "ALTA" : "MEDIA",
                p.nombre() + ": quedan " + p.cantidad().stripTrailingZeros().toPlainString() + " " + p.unidad()
                        + " (mínimo " + p.stockMinimo().stripTrailingZeros().toPlainString() + ")")));
        inventario.alertasMantenimiento().forEach(m -> out.add(new Alerta("MAQUINARIA",
                m.getProximoMantenimiento().isBefore(LocalDate.now()) ? "ALTA" : "MEDIA",
                m.getCodigo() + " " + m.getNombre() + ": mantenimiento programado el " + m.getProximoMantenimiento())));
        if (sst.eventosAbiertos() > 0) {
            out.add(new Alerta("SGSST", "MEDIA", sst.eventosAbiertos() + " casos de SG-SST abiertos sin cerrar"));
        }
        periodos.findAllByOrderByAnioDescMesDesc().stream()
                .filter(p -> PeriodoNomina.ABIERTO.equals(p.getEstado()))
                .forEach(p -> out.add(new Alerta("NOMINA", "MEDIA",
                        "La nómina de " + p.getMes() + "/" + p.getAnio() + " está pendiente de aprobación")));
        long fuera = maquinas.countByEstado(Maquinaria.FUERA_SERVICIO);
        if (fuera > 0) {
            out.add(new Alerta("MAQUINARIA", "ALTA", fuera + " máquinas fuera de servicio"));
        }
        return out;
    }

    private static BigDecimal cumplimiento(Indicadores ind) {
        BigDecimal meta = ind.porFrente().stream().map(c -> c.meta()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return meta.signum() == 0 ? BigDecimal.ZERO
                : ind.totalToneladas().multiply(BigDecimal.valueOf(100)).divide(meta, 1, RoundingMode.HALF_UP);
    }

    private static Kpi kpi(String clave, String nombre, BigDecimal valor, String unidad, BigDecimal anterior) {
        BigDecimal variacion = null;
        if (anterior != null && anterior.signum() != 0) {
            variacion = valor.subtract(anterior).multiply(BigDecimal.valueOf(100))
                    .divide(anterior.abs(), 1, RoundingMode.HALF_UP);
        }
        return new Kpi(clave, nombre, valor, unidad, anterior, variacion);
    }
}
