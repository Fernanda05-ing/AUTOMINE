package com.automine.inventario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automine.common.ApiException;
import com.automine.common.AuditService;
import com.automine.common.Fmt;
import com.automine.contabilidad.ContabilidadService;
import com.automine.contabilidad.MovimientoContable;
import com.automine.inventario.InventarioDtos.MaquinariaRequest;
import com.automine.inventario.InventarioDtos.MantenimientoRequest;
import com.automine.inventario.InventarioDtos.MovimientoRequest;
import com.automine.inventario.InventarioDtos.MovimientoResponse;
import com.automine.inventario.InventarioDtos.ProductoRequest;
import com.automine.inventario.InventarioDtos.ProductoResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    private final Producto.Repo productos;
    private final MovimientoInventario.Repo movimientos;
    private final Maquinaria.Repo maquinas;
    private final ContabilidadService contabilidad;
    private final AuditService audit;

    public List<ProductoResponse> listar(String q, String tipo, boolean soloActivos) {
        return productos.buscar(blank(q), blank(tipo), soloActivos).stream().map(ProductoResponse::of).toList();
    }

    public Producto buscar(Long id) {
        return productos.findById(id).orElseThrow(() -> ApiException.notFound("Producto"));
    }

    /** Servicio AddInventory (RF026). La cantidad inicial queda como primera entrada del kardex. */
    @Transactional
    public ProductoResponse crear(ProductoRequest req) {
        if (productos.existsByCodigoIgnoreCase(req.codigo().trim())) {
            throw ApiException.conflict("Ya existe un producto con el código " + req.codigo());
        }
        Producto p = new Producto();
        aplicar(p, req);
        p.setCantidad(BigDecimal.ZERO);
        p.setActivo(true);
        productos.save(p);
        BigDecimal inicial = req.cantidad() == null ? BigDecimal.ZERO : req.cantidad();
        if (inicial.signum() > 0) {
            registrarMovimiento(p, MovimientoInventario.ENTRADA, inicial, req.costoUnitario(), "Saldo inicial", null);
        }
        audit.registrar("CREAR", "Producto", p.getId(), p.getCodigo() + " " + p.getNombre());
        return ProductoResponse.of(p);
    }

    /** Edita datos maestros. La cantidad solo cambia con movimientos. */
    @Transactional
    public ProductoResponse editar(Long id, ProductoRequest req) {
        Producto p = buscar(id);
        if (!p.getCodigo().equalsIgnoreCase(req.codigo().trim()) && productos.existsByCodigoIgnoreCase(req.codigo())) {
            throw ApiException.conflict("Ya existe un producto con el código " + req.codigo());
        }
        aplicar(p, req);
        audit.registrar("EDITAR", "Producto", id, p.getCodigo());
        return ProductoResponse.of(p);
    }

    @Transactional
    public ProductoResponse cambiarActivo(Long id, boolean activo) {
        Producto p = buscar(id);
        p.setActivo(activo);
        audit.registrar(activo ? "ACTIVAR" : "DESACTIVAR", "Producto", id, p.getCodigo());
        return ProductoResponse.of(p);
    }

    /** RF027: entradas, salidas y ajustes por conteo fisico. */
    @Transactional
    public MovimientoResponse movimiento(MovimientoRequest req) {
        Producto p = buscar(req.productoId());
        if (!p.getActivo()) {
            throw ApiException.badRequest("El producto está inactivo");
        }
        if (!MovimientoInventario.AJUSTE.equals(req.tipo()) && req.cantidad().signum() <= 0) {
            throw ApiException.badRequest("La cantidad debe ser mayor que cero");
        }
        MovimientoInventario m = registrarMovimiento(p, req.tipo(), req.cantidad(), req.costoUnitario(),
                req.motivo().trim(), req.responsable());
        audit.registrar(req.tipo(), "Inventario", p.getId(), p.getCodigo() + " " + Fmt.num(req.cantidad()));
        return MovimientoResponse.of(m);
    }

    /** Usado por SG-SST al entregar EPP: descuenta del almacen. */
    @Transactional
    public void salida(Long productoId, BigDecimal cantidad, String motivo, String responsable) {
        Producto p = buscar(productoId);
        registrarMovimiento(p, MovimientoInventario.SALIDA, cantidad, null, motivo, responsable);
    }

    MovimientoInventario registrarMovimiento(Producto p, String tipo, BigDecimal cantidad, BigDecimal costo,
            String motivo, String responsable) {
        BigDecimal saldo = p.getCantidad();
        BigDecimal nuevo;
        BigDecimal registrada = cantidad;
        switch (tipo) {
            case MovimientoInventario.ENTRADA -> {
                nuevo = saldo.add(cantidad);
                if (costo != null && costo.signum() > 0) {
                    // Costo promedio ponderado
                    BigDecimal valor = saldo.multiply(p.getCostoUnitario()).add(cantidad.multiply(costo));
                    p.setCostoUnitario(valor.divide(nuevo, 2, RoundingMode.HALF_UP));
                }
            }
            case MovimientoInventario.SALIDA -> {
                if (cantidad.compareTo(saldo) > 0) {
                    throw ApiException.badRequest("Stock insuficiente de " + p.getNombre() + ": disponible "
                            + Fmt.num(saldo) + " " + p.getUnidad());
                }
                nuevo = saldo.subtract(cantidad);
            }
            case MovimientoInventario.AJUSTE -> {
                // En el ajuste la cantidad es el conteo fisico; se registra la diferencia.
                nuevo = cantidad;
                registrada = cantidad.subtract(saldo);
            }
            default -> throw ApiException.badRequest("Tipo de movimiento no válido");
        }
        p.setCantidad(nuevo);
        MovimientoInventario m = new MovimientoInventario();
        m.setProducto(p);
        m.setTipo(tipo);
        m.setCantidad(registrada);
        m.setSaldo(nuevo);
        m.setCostoUnitario(costo != null && costo.signum() > 0 ? costo : p.getCostoUnitario());
        m.setMotivo(motivo);
        m.setResponsable(responsable == null || responsable.isBlank() ? AuditService.usuarioActual() : responsable);
        m.setFecha(LocalDateTime.now());
        return movimientos.save(m);
    }

    /** RF029: kardex del producto en un rango de fechas. */
    public List<MovimientoResponse> kardex(Long productoId, LocalDate desde, LocalDate hasta) {
        buscar(productoId);
        ContabilidadService.validarRango(desde, hasta);
        return movimientos.kardex(productoId, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX)).stream()
                .map(MovimientoResponse::of).toList();
    }

    public List<MovimientoResponse> recientes(int limite) {
        return movimientos.recientes(PageRequest.of(0, Math.min(limite, 200))).stream().map(MovimientoResponse::of)
                .toList();
    }

    /** RF028: productos en o por debajo del stock minimo. */
    public List<ProductoResponse> alertasStock() {
        return productos.bajoStock().stream().map(ProductoResponse::of).toList();
    }

    public BigDecimal valorInventario() {
        return productos.valorTotal();
    }

    // ----------------------------- Maquinaria (RF030) -----------------------------

    public List<Maquinaria> maquinas() {
        return maquinas.findAllByOrderByCodigoAsc();
    }

    public Maquinaria maquina(Long id) {
        return maquinas.findById(id).orElseThrow(() -> ApiException.notFound("Máquina"));
    }

    @Transactional
    public Maquinaria crearMaquina(MaquinariaRequest req) {
        if (maquinas.existsByCodigoIgnoreCase(req.codigo().trim())) {
            throw ApiException.conflict("Ya existe una máquina con el código " + req.codigo());
        }
        Maquinaria m = new Maquinaria();
        aplicar(m, req);
        maquinas.save(m);
        audit.registrar("CREAR", "Maquinaria", m.getId(), m.getCodigo());
        return m;
    }

    @Transactional
    public Maquinaria editarMaquina(Long id, MaquinariaRequest req) {
        Maquinaria m = maquina(id);
        if (!m.getCodigo().equalsIgnoreCase(req.codigo().trim()) && maquinas.existsByCodigoIgnoreCase(req.codigo())) {
            throw ApiException.conflict("Ya existe una máquina con el código " + req.codigo());
        }
        aplicar(m, req);
        audit.registrar("EDITAR", "Maquinaria", id, m.getCodigo());
        return m;
    }

    /** Registra un mantenimiento realizado. Si tiene costo, se lleva como gasto contable. */
    @Transactional
    public Maquinaria mantenimiento(Long id, MantenimientoRequest req) {
        Maquinaria m = maquina(id);
        m.setUltimoMantenimiento(req.fecha());
        if (req.proximoMantenimiento() != null) {
            if (!req.proximoMantenimiento().isAfter(req.fecha())) {
                throw ApiException.badRequest("El próximo mantenimiento debe ser posterior al realizado");
            }
            m.setProximoMantenimiento(req.proximoMantenimiento());
        }
        if (req.horometro() != null) {
            if (req.horometro().compareTo(m.getHorometro()) < 0) {
                throw ApiException.badRequest("El horómetro no puede ser menor al actual");
            }
            m.setHorometro(req.horometro());
        }
        m.setEstado(Maquinaria.OPERATIVA);
        m.setObservaciones("Mantenimiento " + Fmt.date(req.fecha()) + ": " + req.descripcion());
        if (req.costo() != null && req.costo().signum() > 0) {
            contabilidad.registrarAutomatico(MovimientoContable.GASTO, req.fecha(),
                    "Mantenimiento " + m.getCodigo() + ": " + req.descripcion(), "Mantenimiento de maquinaria",
                    req.costo(), m.getCodigo(), MovimientoContable.ORIGEN_INVENTARIO);
        }
        audit.registrar("MANTENIMIENTO", "Maquinaria", id, req.descripcion());
        return m;
    }

    public List<Maquinaria> alertasMantenimiento() {
        return maquinas.mantenimientoProximo(LocalDate.now().plusDays(7));
    }

    private void aplicar(Producto p, ProductoRequest r) {
        p.setCodigo(r.codigo().trim().toUpperCase());
        p.setNombre(r.nombre().trim());
        p.setTipo(r.tipo());
        p.setUnidad(r.unidad().trim());
        p.setStockMinimo(r.stockMinimo());
        p.setCostoUnitario(r.costoUnitario());
        p.setUbicacion(blank(r.ubicacion()));
    }

    private void aplicar(Maquinaria m, MaquinariaRequest r) {
        m.setCodigo(r.codigo().trim().toUpperCase());
        m.setNombre(r.nombre().trim());
        m.setTipo(r.tipo().trim());
        m.setMarca(blank(r.marca()));
        m.setModelo(blank(r.modelo()));
        m.setSerial(blank(r.serial()));
        m.setEstado(r.estado());
        m.setHorometro(r.horometro());
        m.setProximoMantenimiento(r.proximoMantenimiento());
        m.setFrenteId(r.frenteId());
        m.setObservaciones(blank(r.observaciones()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
