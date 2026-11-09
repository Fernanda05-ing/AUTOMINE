package com.automine.contabilidad;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.common.ParametroService;
import com.automine.contabilidad.ContabilidadDtos.BalanceGeneral;
import com.automine.contabilidad.ContabilidadDtos.CajaRequest;
import com.automine.contabilidad.ContabilidadDtos.CajaResumen;
import com.automine.contabilidad.ContabilidadDtos.EstadoResultados;
import com.automine.contabilidad.ContabilidadDtos.Linea;
import com.automine.contabilidad.ContabilidadDtos.MovimientoRequest;
import com.automine.contabilidad.ContabilidadDtos.TendenciaMes;
import com.automine.inventario.Producto;
import com.automine.nomina.Nomina;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContabilidadService {

    private final MovimientoContable.Repo movimientos;
    private final CajaMenor.Repo caja;
    private final Producto.Repo productos;
    private final Nomina.Repo nominas;
    private final ParametroService parametros;
    private final AuditService audit;

    /** RF041 / RF042: registro manual de ingresos y gastos. */
    @Transactional
    public MovimientoContable registrar(String tipo, MovimientoRequest req) {
        String categoria = req.categoria() == null || req.categoria().isBlank()
                ? (MovimientoContable.INGRESO.equals(tipo) ? "Venta de mineral" : "Gastos generales")
                : req.categoria().trim();
        MovimientoContable m = registrarAutomatico(tipo, req.fecha(), req.concepto().trim(), categoria, req.valor(),
                req.referencia(), MovimientoContable.ORIGEN_MANUAL);
        audit.registrar("REGISTRAR_" + tipo, "Contabilidad", m.getId(), m.getConcepto() + " " + Fmt.money(m.getValor()));
        return m;
    }

    /** Movimientos que generan otros modulos (pago de nomina, caja menor, mantenimiento...). */
    @Transactional
    public MovimientoContable registrarAutomatico(String tipo, LocalDate fecha, String concepto, String categoria,
            BigDecimal valor, String referencia, String origen) {
        if (fecha.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("La fecha no puede ser futura");
        }
        MovimientoContable m = new MovimientoContable();
        m.setTipo(tipo);
        m.setFecha(fecha);
        m.setConcepto(concepto);
        m.setCategoria(categoria);
        m.setValor(valor);
        m.setReferencia(referencia == null || referencia.isBlank() ? null : referencia.trim());
        m.setOrigen(origen);
        m.setRegistradoPor(AuditService.usuarioActual());
        return movimientos.save(m);
    }

    public List<MovimientoContable> listar(String tipo, LocalDate desde, LocalDate hasta) {
        return movimientos.findByTipoAndFechaBetweenOrderByFechaDescIdDesc(tipo, desde, hasta);
    }

    @Transactional
    public void eliminar(Long id) {
        MovimientoContable m = movimientos.findById(id).orElseThrow(() -> ApiException.notFound("Movimiento"));
        if (!MovimientoContable.ORIGEN_MANUAL.equals(m.getOrigen())) {
            throw ApiException.badRequest("Solo se pueden anular movimientos registrados manualmente");
        }
        movimientos.delete(m);
        audit.registrar("ELIMINAR_" + m.getTipo(), "Contabilidad", id, m.getConcepto() + " " + Fmt.money(m.getValor()));
    }

    // ------------------------- Caja menor (RF043) -------------------------

    public BigDecimal saldoCaja() {
        return caja.findFirstByOrderByIdDesc().map(CajaMenor::getSaldo).orElse(BigDecimal.ZERO);
    }

    public CajaResumen caja(LocalDate desde, LocalDate hasta) {
        return new CajaResumen(saldoCaja(), parametros.decimal("CAJA_MENOR_TOPE"),
                caja.findByFechaBetweenOrderByIdDesc(desde, hasta));
    }

    @Transactional
    public CajaMenor movimientoCaja(CajaRequest req) {
        BigDecimal saldo = saldoCaja();
        BigDecimal nuevo;
        if (CajaMenor.GASTO.equals(req.tipo())) {
            if (req.valor().compareTo(saldo) > 0) {
                throw ApiException.badRequest("Saldo insuficiente en caja menor (" + Fmt.money(saldo) + ")");
            }
            nuevo = saldo.subtract(req.valor());
        } else {
            BigDecimal tope = parametros.decimal("CAJA_MENOR_TOPE");
            nuevo = saldo.add(req.valor());
            if (nuevo.compareTo(tope) > 0) {
                throw ApiException.badRequest("La caja menor no puede superar el tope de " + Fmt.money(tope));
            }
        }
        CajaMenor c = new CajaMenor();
        c.setFecha(req.fecha());
        c.setTipo(req.tipo());
        c.setConcepto(req.concepto().trim());
        c.setValor(req.valor());
        c.setSaldo(nuevo);
        c.setSoporte(req.soporte());
        c.setResponsable(AuditService.usuarioActual());
        caja.save(c);
        if (CajaMenor.GASTO.equals(req.tipo())) {
            registrarAutomatico(MovimientoContable.GASTO, req.fecha(), "Caja menor: " + c.getConcepto(),
                    "Caja menor", req.valor(), req.soporte(), MovimientoContable.ORIGEN_CAJA);
        }
        audit.registrar("CAJA_" + req.tipo(), "CajaMenor", c.getId(), c.getConcepto() + " " + Fmt.money(c.getValor()));
        return c;
    }

    // ----------------------- Estados financieros -----------------------

    /** RF044: balance general a la fecha de corte. */
    public BalanceGeneral balance(LocalDate corte) {
        BigDecimal ingresos = movimientos.acumulado(MovimientoContable.INGRESO, corte);
        BigDecimal gastos = movimientos.acumulado(MovimientoContable.GASTO, corte);
        BigDecimal saldoCaja = saldoCaja();
        BigDecimal reposiciones = caja.findAll().stream()
                .filter(c -> CajaMenor.REPOSICION.equals(c.getTipo()) && !c.getFecha().isAfter(corte))
                .map(CajaMenor::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Los gastos de caja menor salen de la caja, no del banco: se suman de vuelta y se restan las reposiciones.
        BigDecimal gastosCaja = caja.findAll().stream()
                .filter(c -> CajaMenor.GASTO.equals(c.getTipo()) && !c.getFecha().isAfter(corte))
                .map(CajaMenor::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bancos = ingresos.subtract(gastos).add(gastosCaja).subtract(reposiciones);
        BigDecimal inventario = productos.valorTotal();

        List<Linea> activos = List.of(new Linea("Efectivo y bancos", bancos), new Linea("Caja menor", saldoCaja),
                new Linea("Inventarios", inventario));
        BigDecimal totalActivos = bancos.add(saldoCaja).add(inventario);

        BigDecimal nominaPorPagar = nominas.porPagar();
        List<Linea> pasivos = List.of(new Linea("Nómina por pagar", nominaPorPagar));

        BigDecimal resultados = ingresos.subtract(gastos);
        BigDecimal capital = totalActivos.subtract(nominaPorPagar).subtract(resultados);
        List<Linea> patrimonio = List.of(new Linea("Capital y aportes", capital),
                new Linea("Resultados acumulados", resultados));
        return new BalanceGeneral(corte, activos, totalActivos, pasivos, nominaPorPagar, patrimonio,
                capital.add(resultados));
    }

    /** RF045: estado de resultados. */
    public EstadoResultados estadoResultados(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        List<Linea> ingresos = movimientos.porCategoria(MovimientoContable.INGRESO, desde, hasta).stream()
                .map(t -> new Linea(t.categoria(), t.total())).toList();
        List<Linea> gastos = movimientos.porCategoria(MovimientoContable.GASTO, desde, hasta).stream()
                .map(t -> new Linea(t.categoria(), t.total())).toList();
        BigDecimal ti = movimientos.total(MovimientoContable.INGRESO, desde, hasta);
        BigDecimal tg = movimientos.total(MovimientoContable.GASTO, desde, hasta);
        BigDecimal utilidad = ti.subtract(tg);
        BigDecimal margen = ti.signum() == 0 ? BigDecimal.ZERO
                : utilidad.multiply(BigDecimal.valueOf(100)).divide(ti, 2, RoundingMode.HALF_UP);
        return new EstadoResultados(desde, hasta, ingresos, ti, gastos, tg, utilidad, margen);
    }

    public List<TendenciaMes> tendencia(LocalDate desde, LocalDate hasta) {
        Map<String, BigDecimal[]> meses = new TreeMap<>();
        LocalDate cursor = desde.withDayOfMonth(1);
        while (!cursor.isAfter(hasta)) {
            meses.put(String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue()),
                    new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            cursor = cursor.plusMonths(1);
        }
        for (MovimientoContable.TotalMes t : movimientos.porMes(desde, hasta)) {
            BigDecimal[] par = meses.get(String.format("%04d-%02d", t.anio(), t.mes()));
            if (par != null) {
                par[MovimientoContable.INGRESO.equals(t.tipo()) ? 0 : 1] = t.total();
            }
        }
        List<TendenciaMes> out = new ArrayList<>();
        meses.forEach((k, v) -> out.add(new TendenciaMes(Integer.parseInt(k.substring(0, 4)),
                Integer.parseInt(k.substring(5)), v[0], v[1], v[0].subtract(v[1]))));
        return out;
    }

    public static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw ApiException.badRequest("La fecha inicial debe ser anterior a la final");
        }
    }
}
